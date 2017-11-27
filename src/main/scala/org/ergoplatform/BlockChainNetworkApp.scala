package org.ergoplatform

import akka.actor.ActorSystem

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Random

object BlockChainNetworkApp extends App {
  implicit val actorSystem = ActorSystem("blockchain")

  val blockChainWithGenesis = BlockChain.withGenesis

  val nodes = for (i <- 1 to 5) yield {
    new BlockChainNode(s"node-$i", blockChainWithGenesis, Seq.empty)
  }

  val firstNode = nodes.head
  nodes.tail.foreach(_.connectTo(firstNode))

  def sendNewBlockToRandomNode: Future[Unit] = {
    val randomNode = new Random().shuffle(nodes).head
    for {
      randomNodeBlockChain <- randomNode.getBlockСhain
    } yield randomNode.sendBlock(Block.forge(randomNodeBlockChain.lastBlockId))
  }

  def getAllNodesBlockChains: Future[Map[String, BlockChain]] = {
    Future.sequence(nodes.map(n => n.getBlockСhain.map(bc => n.name -> bc))).map(_.toMap)
  }

  actorSystem.scheduler.schedule(1.seconds, 1.seconds) {
    for {
      _ <- sendNewBlockToRandomNode
      blockChains <- getAllNodesBlockChains
    } {
      println(s"Check at: ${System.currentTimeMillis()}")
      blockChains.toSeq.sortBy(_._1).foreach(p => println(s"${p._1}: \n\t${p._2}"))
      println()
    }
  }

  sys.addShutdownHook {
    Await.result(actorSystem.terminate(), 10.seconds)
  }
}
