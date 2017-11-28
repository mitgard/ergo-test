package org.ergoplatform

import akka.actor.{Actor, ActorRef, Props, Terminated}

import scala.util.{Failure, Success}

object BlockChainNodeActor {
  def props(startBlockChain: BlockChain, knownPeers: Seq[ActorRef]): Props = Props(new BlockChainNodeActor(startBlockChain, knownPeers))

  case object GetBlockChain

  case object GetConnectedPeers

  case class ConnectTo(peer: ActorRef)

}

class BlockChainNodeActor(startBlockChain: BlockChain, knownPeers: Seq[ActorRef]) extends Actor {

  import BlockChainNodeActor._

  override def preStart(): Unit =
    context.become(active(startBlockChain, knownPeers))

  private def active(blockChain: BlockChain, peers: Seq[ActorRef]): Receive = {
    case GetConnectedPeers =>
      sender() ! peers
    case GetBlockChain =>
      sender() ! blockChain
    case ConnectTo(node) =>
      context.watch(node)
      context.become(active(blockChain, peers :+ node))
    case Terminated(terminatedNode) =>
      context.become(active(blockChain, peers.filter(_ != terminatedNode)))
    case b: Block => blockChain.append(b) match {
      case Success(newBlockChain) =>
        context.become(active(newBlockChain, knownPeers))
      case Failure(f) =>
        println(s"Error on apply block $b to blockchain $blockChain: $f")
    }
  }

  override def receive = Actor.emptyBehavior
}
