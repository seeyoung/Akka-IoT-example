package com.lightbend.akka.sample;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.testkit.javadsl.TestKit;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class DeviceManagerTest {
    static ActorSystem system;

    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
    }

    @AfterClass
    public static void teardown() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    TestKit probe;
    ActorRef managerActor;

    @Before
    public void prepareGroupActor(){
        probe = new TestKit(system);
        managerActor = system.actorOf(DeviceManager.props());
    }

    @Test
    public void testRegisterDeviceActor(){
        managerActor.tell(new DeviceManager.RequestTrackDevice("group", "device1"), probe.getRef());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
        ActorRef deviceActor1 = probe.getLastSender();

        managerActor.tell(new DeviceManager.RequestTrackDevice("group", "device2"),probe.getRef());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
        ActorRef deviceActor2 = probe.getLastSender();
        assertNotEquals(deviceActor1, deviceActor2);

        //Check that the device actors are working
        deviceActor1.tell(new Device.RecordTemperature(0L, 1.0), probe.getRef());
        assertEquals(0L, probe.expectMsgClass(Device.TemperatureRecoded.class).requestId);
        deviceActor2.tell(new Device.RecordTemperature(1L, 2.0), probe.getRef());
        assertEquals(1L, probe.expectMsgClass(Device.TemperatureRecoded.class).requestId);
    }

    @Test
    public void testReturnSameActorForSameGroupId(){
        managerActor.tell(new DeviceManager.RequestTrackDevice("group", "device1"), probe.getRef());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
        ActorRef deviceActor1 = probe.getLastSender();

        managerActor.tell(new DeviceManager.RequestTrackDevice("group", "device2"),probe.getRef());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
        ActorRef deviceActor2 = probe.getLastSender();
        assertNotEquals(deviceActor1, deviceActor2);

        managerActor.tell(new DeviceManager.RequestGroupList(0L), probe.getRef());
        DeviceManager.ReplyGroupList reply = probe.expectMsgClass(DeviceManager.ReplyGroupList.class);
        assertEquals(1, reply.ids.size());
    }

    @Test
    public void testListActiveGroups(){
        managerActor.tell(new DeviceManager.RequestTrackDevice("group1", "device1"),probe.getRef());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);

        managerActor.tell(new DeviceManager.RequestTrackDevice("group2", "device2"),probe.getRef());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);

        managerActor.tell(new DeviceManager.RequestGroupList(0L),probe.getRef());
        DeviceManager.ReplyGroupList reply = probe.expectMsgClass(DeviceManager.ReplyGroupList.class);
        assertEquals(0L, reply.requestId);
        assertEquals(Stream.of("group1", "group2").collect(Collectors.toSet()), reply.ids);
        assertEquals(2, reply.actors.size());
    }

    @Test
    public void testListActiveGroupsAfterOneShutdown(){
        managerActor.tell(new DeviceManager.RequestTrackDevice("group", "device1"), probe.getRef());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);


        managerActor.tell(new DeviceManager.RequestTrackDevice("group1", "device1"), probe.getRef());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);

        managerActor.tell(new DeviceManager.RequestGroupList(0L),probe.getRef());
        DeviceManager.ReplyGroupList reply = probe.expectMsgClass(DeviceManager.ReplyGroupList.class);
        assertEquals(0L, reply.requestId);
        assertEquals(Stream.of("group", "group1").collect(Collectors.toSet()), reply.ids);

        ActorRef toShutDown = (ActorRef) reply.actors.toArray()[0];

        probe.watch(toShutDown);
        toShutDown.tell(PoisonPill.getInstance(), ActorRef.noSender());
        probe.expectTerminated(toShutDown);

        // using awaitAssert to retry because it might take longer for the managerActor
        // to see the Terminated, that order is undefined
        probe.awaitAssert(() -> {
            managerActor.tell(new DeviceManager.RequestGroupList(1L), probe.getRef());
            DeviceManager.ReplyGroupList r = probe.expectMsgClass(DeviceManager.ReplyGroupList.class);
            assertEquals(1L, r.requestId);
            assertEquals(1, r.ids.size());
            return null;
        });
    }
}
