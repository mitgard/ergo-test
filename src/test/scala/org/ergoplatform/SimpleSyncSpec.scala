package org.ergoplatform

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.ergoplatform.BlockChainNodeActor.{ConnectTo, GetBlockChain, GetConnectedPeers}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class SimpleSyncSpec extends TestKit(ActorSystem("BlockChainNodeSpec")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "An BlockChain node actor" must {
    "synchronize with a known node" in {
      val blockChainWithOnlyGenesis = BlockChain.withGenesis
      val blockChainWithNewBlock = blockChainWithOnlyGenesis.append(Block.forge(blockChainWithOnlyGenesis.lastBlockId)).get

      val nodeWithNewBlock = system.actorOf(BlockChainNodeActor.props(blockChainWithNewBlock, Seq.empty))
      val node = system.actorOf(BlockChainNodeActor.props(blockChainWithOnlyGenesis, Seq(nodeWithNewBlock)))

      node ! GetBlockChain

      expectMsg(blockChainWithOnlyGenesis)
    }

    "synchronize with new outgoing connected node" in {
      val blockChainWithOnlyGenesis = BlockChain.withGenesis
      val blockChainWithNewBlock = blockChainWithOnlyGenesis.append(Block.forge(blockChainWithOnlyGenesis.lastBlockId)).get

      val nodeWithNewBlock = system.actorOf(BlockChainNodeActor.props(blockChainWithNewBlock, Seq.empty))
      val node = system.actorOf(BlockChainNodeActor.props(blockChainWithOnlyGenesis, Seq.empty))

      node ! ConnectTo(nodeWithNewBlock)

      Thread.sleep(1000)

      node ! GetBlockChain
      expectMsg(blockChainWithOnlyGenesis)
    }

    "synchronize with new incoming connected node" in {
      val blockChainWithOnlyGenesis = BlockChain.withGenesis
      val blockChainWithNewBlock = blockChainWithOnlyGenesis.append(Block.forge(blockChainWithOnlyGenesis.lastBlockId)).get

      val nodeWithNewBlock = system.actorOf(BlockChainNodeActor.props(blockChainWithNewBlock, Seq.empty))
      val node = system.actorOf(BlockChainNodeActor.props(blockChainWithOnlyGenesis, Seq.empty))

      nodeWithNewBlock ! ConnectTo(node)

      Thread.sleep(1000)

      node ! GetBlockChain
      expectMsg(blockChainWithOnlyGenesis)
    }

    "synchronize network with the best chain in star topology" in {
      val blockChainWithOnlyGenesis = BlockChain.withGenesis

      val blockChainWithBestFork = blockChainWithOnlyGenesis.append(Block(blockChainWithOnlyGenesis.lastBlockId, Block.generateId, 500)).get
      val someOtherForks = Seq.fill(5)(blockChainWithOnlyGenesis.append(Block.forge(blockChainWithOnlyGenesis.lastBlockId)).get)

      val allForks = someOtherForks :+ blockChainWithBestFork

      val nodesWithDifferentBlockChains = allForks.map(bc =>
        system.actorOf(BlockChainNodeActor.props(bc, Seq.empty)))

      val node = system.actorOf(BlockChainNodeActor.props(blockChainWithOnlyGenesis, Seq.empty))

      nodesWithDifferentBlockChains.foreach(node => node ! ConnectTo(node))

      Thread.sleep(1000)

      val allNodes = node +: nodesWithDifferentBlockChains

      allNodes.foreach(node => {
        node ! GetBlockChain
        expectMsg(blockChainWithBestFork)
      })
    }

    "synchronize network with the best chain in chain topology" in {
      val blockChainWithOnlyGenesis = BlockChain.withGenesis

      val blockChainWithBestFork = blockChainWithOnlyGenesis.append(Block(blockChainWithOnlyGenesis.lastBlockId, Block.generateId, 500)).get
      val someOtherForks = Seq.fill(5)(blockChainWithOnlyGenesis.append(Block.forge(blockChainWithOnlyGenesis.lastBlockId)).get)

      val allForks = someOtherForks :+ blockChainWithBestFork

      val nodesWithDifferentBlockChains = allForks.map(bc =>
        system.actorOf(BlockChainNodeActor.props(bc, Seq.empty)))

      val node = system.actorOf(BlockChainNodeActor.props(blockChainWithOnlyGenesis, Seq.empty))

      val allNodes = node +: nodesWithDifferentBlockChains

      allNodes.sliding(2).foreach {
        case Seq(from, to) => from ! ConnectTo(to)
      }

      Thread.sleep(1000)

      allNodes.foreach(node => {
        node ! GetBlockChain
        expectMsg(blockChainWithBestFork)
      })
    }
  }
}