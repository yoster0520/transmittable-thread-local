@file:JvmName("AkkaAgentDemo")
package com.alibaba.demo.ttl.agent

import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import com.alibaba.ttl.TransmittableThreadLocal

private val actorSystem: akka.actor.typed.ActorSystem<Command> = akka.actor.typed.ActorSystem.create(Behaviors.setup { context: ActorContext<Command>? -> Accountant(context!!) }, "MainActor")
    .logConfiguration()

fun main() {
    stringTransmittableThreadLocal.set("AAAAAAAAAAAA")
    actorSystem.tell(Command())
}

class Command {

}

private val stringTransmittableThreadLocal = TransmittableThreadLocal<String>()

class Accountant : AbstractBehavior<Command> {

    constructor(context: ActorContext<Command>) : super(context)

    override fun createReceive(): Receive<Command?>? {
        return newReceiveBuilder()
            .onMessage(Command::class.java, this::onCommand)
            .build()
    }

    private fun onCommand(command: Command): Accountant {
        println("stringTransmittableThreadLocal: ${stringTransmittableThreadLocal.get()}")
        return this
    }

}
