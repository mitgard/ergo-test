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
    "accept valid block" in {
      val bc = BlockChain.withGenesis
      val node = system.actorOf(BlockChainNodeActor.props(bc, Seq.empty))

      val blockChainWithNewBlock = bc.append(Block.forge(bc.lastBlockId)).get

      node ! blockChainWithNewBlock.blocks.values.last
      node ! GetBlockChain

      expectMsg(blockChainWithNewBlock)
    }

    "not accept second genesis block" in {
      val bc = BlockChain.withGenesis
      val node = system.actorOf(BlockChainNodeActor.props(bc, Seq.empty))

      val invalidBlock = Block.forge()

      node ! invalidBlock
      node ! GetBlockChain
      expectMsg(bc)
    }

    "not accept invalid by id block" in {
      val bc = BlockChain.withGenesis
      val node = system.actorOf(BlockChainNodeActor.props(bc, Seq.empty))

      val invalidBlock = Block.forge(bc.lastBlockId).copy(id = "123")

      node ! invalidBlock
      node ! GetBlockChain
      expectMsg(bc)
    }

    "not accept invalid by score block" in {
      val bc = BlockChain.withGenesis
      val node = system.actorOf(BlockChainNodeActor.props(bc, Seq.empty))

      val invalidBlock = Block.forge(bc.lastBlockId).copy(score = -1)

      node ! invalidBlock
      node ! GetBlockChain
      expectMsg(bc)
    }

    "not accept invalid by parent block" in {
      val bc = BlockChain.withGenesis
      val node = system.actorOf(BlockChainNodeActor.props(bc, Seq.empty))

      val invalidBlock = Block.forge().copy(parentId = Some("1234"))

      node ! invalidBlock
      node ! GetBlockChain
      expectMsg(bc)
    }

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

    "synchronize with a known node" in {
      val blockChainWithOnlyGenesis = BlockChain.withGenesis
      val blockChainWithNewBlock = blockChainWithOnlyGenesis.append(Block.forge(blockChainWithOnlyGenesis.lastBlockId)).get

      val nodeWithNewBlock = system.actorOf(BlockChainNodeActor.props(blockChainWithNewBlock, Seq.empty))
      val node = system.actorOf(BlockChainNodeActor.props(blockChainWithOnlyGenesis, Seq(nodeWithNewBlock)))

      node ! GetBlockChain

      expectMsg(blockChainWithOnlyGenesis)
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