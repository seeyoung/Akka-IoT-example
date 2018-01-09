package com.lightbend.akka.sample;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DeviceManager extends AbstractActor{
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    public static Props props(){
        return Props.create(DeviceManager.class);
    }

    public static final class RequestTrackDevice {
        public final String groupId;
        public final String deviceId;

        public RequestTrackDevice(String groupId, String deviceId) {
            this.groupId = groupId;
            this.deviceId = deviceId;
        }
    }

    public static final class DeviceRegistered {
    }

    public static final class RequestGroupList {
        final long requestId;

        public RequestGroupList(long requestId){
            this.requestId = requestId;
        }
    }

    public static final class ReplyGroupList {
        final long requestId;
        final Set<String> ids;
        final Set<ActorRef> actors;

        public ReplyGroupList(long requestId, Set<String > ids, Set<ActorRef> actors){
            this.ids = ids;
            this.requestId = requestId;
            this.actors = actors;
        }
    }

    final Map<String , ActorRef> groupIdToActor = new HashMap<>();
    final Map<ActorRef , String> actorToGroupId = new HashMap<>();

    @Override
    public void preStart(){
        log.info("DeviceManager started");
    }

    @Override
    public void postStop(){
        log.info("DeviceManager stopped");
    }

    private void onTrackDevice(RequestTrackDevice trackMsg){
        String groupId = trackMsg.groupId;
        ActorRef ref = groupIdToActor.get(groupId);
        if(ref != null){
            ref.forward(trackMsg, getContext());
        }else {
            log.info("Creating device group actor for {}", groupId);
            ActorRef groupActor = getContext().actorOf(DeviceGroup.props(groupId), "group-"+groupId);
            getContext().watch(groupActor);
            groupActor.forward(trackMsg, getContext());
            groupIdToActor.put(groupId, groupActor);
            actorToGroupId.put(groupActor, groupId);
        }
    }

    private void onGroupList(RequestGroupList r){
        getSender().tell(new ReplyGroupList(r.requestId, groupIdToActor.keySet(),actorToGroupId.keySet()),getSelf());
    }


    private void onTerminated(Terminated t){
        ActorRef groupActor = t.getActor();
        String groupId = actorToGroupId.get(groupActor);
        log.info("Device group actor for {} has been terminated", groupId);
        actorToGroupId.remove(groupActor);
        groupIdToActor.remove(groupId);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(RequestTrackDevice.class, this::onTrackDevice)
                .match(Terminated.class, this::onTerminated)
                .match(RequestGroupList.class, this::onGroupList)
                .build();
    }
}
