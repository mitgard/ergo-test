package org.ergoplatform

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.ergoplatform.BlockChainNodeActor.{ConnectTo, GetBlockChain, GetConnectedPeers}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class ConnectionSpec extends TestKit(ActorSystem("ConnectionSpec")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "An BlockChain node actor" must {
    "connects to a known node" in {
      val blockChainWithOnlyGenesis = BlockChain.withGenesis
      val blockChainWithNewBlock = blockChainWithOnlyGenesis.append(Block.forge(blockChainWithOnlyGenesis.lastBlockId)).get

      val nodeWithNewBlock = system.actorOf(BlockChainNodeActor.props(blockChainWithNewBlock, Seq.empty))
      val node = system.actorOf(BlockChainNodeActor.props(blockChainWithOnlyGenesis, Seq(nodeWithNewBlock)))

      node ! GetConnectedPeers
      expectMsg(Seq(nodeWithNewBlock))

      nodeWithNewBlock ! GetConnectedPeers
      expectMsg(Seq(node))
    }

    "connects with new outgoing connected node" in {
      val blockChainWithOnlyGenesis = BlockChain.withGenesis
      val blockChainWithNewBlock = blockChainWithOnlyGenesis.append(Block.forge(blockChainWithOnlyGenesis.lastBlockId)).get

      val nodeWithNewBlock = system.actorOf(BlockChainNodeActor.props(blockChainWithNewBlock, Seq.empty))
      val node = system.actorOf(BlockChainNodeActor.props(blockChainWithOnlyGenesis, Seq.empty))

      node ! ConnectTo(nodeWithNewBlock)

      Thread.sleep(1000)

      node ! GetConnectedPeers
      expectMsg(Seq(nodeWithNewBlock))
    }

    "connects with new incoming connected node" in {
      val blockChainWithOnlyGenesis = BlockChain.withGenesis
      val blockChainWithNewBlock = blockChainWithOnlyGenesis.append(Block.forge(blockChainWithOnlyGenesis.lastBlockId)).get

      val nodeWithNewBlock = system.actorOf(BlockChainNodeActor.props(blockChainWithNewBlock, Seq.empty))
      val node = system.actorOf(BlockChainNodeActor.props(blockChainWithOnlyGenesis, Seq.empty))

      nodeWithNewBlock ! ConnectTo(node)

      Thread.sleep(1000)

      node ! GetConnectedPeers
      expectMsg(Seq(nodeWithNewBlock))
    }
}