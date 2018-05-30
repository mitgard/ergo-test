package org.ergoplatform

import akka.actor.{Actor, ActorRef, Props, Terminated}
import akka.actor.Stash
import scala.util.{Failure, Success}

object BlockChainNodeActor {
  def props(startBlockChain: BlockChain, knownPeers: Seq[ActorRef]): Props = Props(new BlockChainNodeActor(startBlockChain, knownPeers))

  case object GetBlockChain

  case object GetConnectedPeers

  case class ConnectTo(peer: ActorRef)

  case class GetLastBlockAfter(block: Block)

  case class LastBlock(block: Block, height: Int = 0)
}

class BlockChainNodeActor(startBlockChain: BlockChain, knownPeers: Seq[ActorRef]) extends Actor with Stash {

  import BlockChainNodeActor._
  knownPeers.foreach(_ ! GetLastBlockAfter(startBlockChain.lastBlock.get))

  override def preStart(): Unit ={
    if(knownPeers.isEmpty)
    context.become(active(startBlockChain, knownPeers))
    else {
      knownPeers.foreach(_ ! ConnectTo(self))
      context.become(receive)
    }
  }

  private def active(blockChain: BlockChain, peers: Seq[ActorRef]): Receive = {
    case GetConnectedPeers =>
      sender() ! peers
    case GetBlockChain =>
      sender() ! blockChain
    case ConnectTo(node) =>
      context.watch(node)
      println(s" Self $self connect to $node")
      if(!peers.contains(node)){
        node ! ConnectTo(self)
        node ! LastBlock(blockChain.lastBlock.get)
        context.become(active(blockChain, peers :+ node))
      }
      else
      context.become(active(blockChain, peers))
    case Terminated(terminatedNode) =>
      context.become(active(blockChain, peers.filter(_ != terminatedNode)))
    case b: Block => blockChain.append(b) match {
      case Success(newBlockChain) =>
        context.become(active(newBlockChain, knownPeers))
      case Failure(f) =>
        println(s"Error on apply block $b to blockchain $blockChain: $f")
    }
    case getLastBlockAfter: GetLastBlockAfter =>
      println(s"I'm $self send $sender() block: ${blockChain.filter(_.parentId.contains(getLastBlockAfter.block.id)).last}")
      val lastBlockChain = blockChain.filter(_.parentId.contains(getLastBlockAfter.block.id)).last
      sender() ! LastBlock(lastBlockChain)
    case lastBlock: LastBlock =>
      blockChain.append(lastBlock.block) match {
        case Success(newBlockChain) =>
          println(s"checked at ${System.currentTimeMillis()}   $self new BlockChain = $newBlockChain")
          context.become(active(newBlockChain, knownPeers))
        case Failure(f) =>
          println(s"Error on synchronized block ${lastBlock.block} with error: $f")
      }
  }

  override def receive: Receive = {
    case lastBlock: LastBlock =>
      startBlockChain.append(lastBlock.block) match {
        case Success(newBlockChain) =>
          println(s"checked at ${System.currentTimeMillis()}   $self new BlockChain = $newBlockChain")
          unstashAll()
          context.become(active(newBlockChain, knownPeers))
        case Failure(f) =>
          println(s"Error on synchronized block ${lastBlock.block} with error: $f")
      }
    case _ =>
      if(knownPeers.isEmpty)
        context.become(active(startBlockChain, knownPeers))
      else
      stash()
  }

}
