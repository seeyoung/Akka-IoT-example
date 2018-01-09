package com.lightbend.akka.sample;

import akka.actor.*;

class SupervisingActor extends AbstractActor {

    ActorRef child = getContext().actorOf(Props.create(SupervisedActor.class), "supervised-actor");
    @Override
    public Receive createReceive() {
        return receiveBuilder().matchEquals("failChild", f -> {
            child.tell("fail", getSelf());
        }).build();
    }
}

class SupervisedActor extends AbstractActor{
    @Override
    public void preStart(){
        System.out.println("supervised actor started");
    }

    @Override
    public void postStop(){
        System.out.println("supervised actor stopped");
    }

    @Override
    public Receive createReceive(){
        return receiveBuilder()
                .matchEquals("fail", f -> {
                    System.out.println("supervised actor fails now");
                    throw new Exception("I failed!");
                }).build();
    }
}

public class ActorSupervisor {
    public static void main(String[] args){
        ActorSystem system = ActorSystem.create("test-system");

        ActorRef supervisingActor = system.actorOf(Props.create(SupervisingActor.class), "supervising-actor");
        supervisingActor.tell("failChild", ActorRef.noSender());
    }
}
