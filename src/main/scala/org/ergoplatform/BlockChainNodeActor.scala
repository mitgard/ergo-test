package org.ergoplatform

import akka.actor.{Actor, ActorRef, Props, Terminated}
import akka.actor.Stash
import org.ergoplatform.BlockChainNodeActor.limit

import scala.util.{Failure, Success}

object BlockChainNodeActor {
  def props(startBlockChain: BlockChain, knownPeers: Seq[ActorRef]): Props = Props(new BlockChainNodeActor(startBlockChain, knownPeers))

  private val limit = 10
  private val offset = 0

  case object GetBlockChain

  case object GetConnectedPeers

  case class ConnectTo(peer: ActorRef)

//  case class GetLastBlockAfter(block: Block)

  case class GetBlocks(limit: Int = limit, offset: Int = offset)

  case class Blocks(blocks: Iterable[Block], offset: Int = offset, size: Int)

//  case class LastBlock(block: Block, height: Int = 0)
}

class BlockChainNodeActor(startBlockChain: BlockChain, knownPeers: Seq[ActorRef]) extends Actor with Stash {

  import BlockChainNodeActor._

  override def preStart(): Unit ={
    if(knownPeers.nonEmpty) {
      println(s"Prestart $knownPeers")
      knownPeers.foreach(_ ! GetBlocks())
      knownPeers.foreach(_ ! ConnectTo(self))
      context.become(receive)
    }
    else
      context.become(active(startBlockChain, knownPeers))
  }

  private def active(blockChain: BlockChain, peers: Seq[ActorRef]): Receive = {
    case GetConnectedPeers =>
      sender() ! peers
      println(s"Actor $self sended peers: $peers actor $sender() at ${System.currentTimeMillis()}")
      if(!peers.contains(sender()))
        context.become(active(blockChain, peers :+ sender()))
    case GetBlockChain =>
      println(s" Self $self blockchain $blockChain")
      sender() ! blockChain
    case ConnectTo(node) =>
      println(s"$self ConnectTo($node) at ${System.currentTimeMillis()}")
      context.watch(node)
      if(!peers.contains(node)) {
        node ! ConnectTo(self)
        node ! GetBlocks()
        node ! Blocks(blockChain.slice(offset, limit + offset), offset, blockChain.size)
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
    case Blocks(blocks, offset, size) =>
      if(blocks.isEmpty)
        context.become(active(blockChain, knownPeers))
      else {
        val newBc = appendBlocks(blocks, blockChain)
        if(offset < size)
        sender() ! GetBlocks( limit, limit + offset)
        context.become(active(newBc, knownPeers))
      }
    case GetBlocks(limit, offset) =>
      sender() ! Blocks(blockChain.slice(offset, limit + offset), offset, blockChain.size)
  }

  override def receive: Receive = {
    case Blocks(blocks, offset, size) =>
      if(blocks.isEmpty)
        context.become(active(startBlockChain, knownPeers))
      else {
        val newBc = appendBlocks(blocks, startBlockChain)
        if(offset < size)
          sender() ! GetBlocks( limit, limit + offset)
        unstashAll()
        context.become(active(newBc, knownPeers))
      }
    case GetBlocks(limit, offset) =>
      sender() ! Blocks(startBlockChain.slice(offset, limit + offset), offset, startBlockChain.size)
    case GetConnectedPeers =>
      sender() ! knownPeers
    case _ =>
      println(s"All message stashed. Actor $self")
      stash()
  }

  private def appendBlocks(blocks: Iterable[Block], blockChain: BlockChain): BlockChain = {
    if(blocks.nonEmpty)
    blockChain.append(blocks.head) match {
      case Success(newBlockChain) =>
        if(blocks.tail.nonEmpty)
          appendBlocks(blocks.tail, newBlockChain)
        else
          newBlockChain
      case Failure(_) =>
        if(blocks.tail.isEmpty)
          blockChain
        else
          appendBlocks(blocks.tail, blockChain)
    }
    else
      blockChain
  }
}
