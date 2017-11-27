package org.ergoplatform

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import org.ergoplatform.BlockChainNodeActor.{ConnectTo, GetBlockChain, GetConnectedPeers}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class SimpleSyncSpec extends TestKit(ActorSystem("BlockChainNodeSpec")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "An BlockChain node actor" must {
    "accept valid genesis block" in {
      val emptyNode = system.actorOf(BlockChainNodeActor.props(BlockChain.empty, Seq.empty))

      val bc = BlockChain.withGenesis

      emptyNode ! bc.blocks.values.last
      emptyNode ! GetBlockChain

      expectMsg(bc)
    }

    "accept valid block" in {
      val bc = BlockChain.withGenesis
      val node = system.actorOf(BlockChainNodeActor.props(bc, Seq.empty))

      val newBc = bc.append(Block.forge(bc.lastBlockId)).get

      node ! newBc.blocks.values.last
      node ! GetBlockChain

      expectMsg(newBc)
    }

    "not accept invalid genesis block" in {
      val emptyNode = system.actorOf(BlockChainNodeActor.props(BlockChain.empty, Seq.empty))

      val bc = BlockChain.withGenesis

      emptyNode ! bc.blocks.values.head.copy(parentId = Some("123"))
      emptyNode ! GetBlockChain

      expectMsg(BlockChain.empty)
    }

    "not accept second genesis block" in {
      val emptyNode = system.actorOf(BlockChainNodeActor.props(BlockChain.empty, Seq.empty))

      val bc = BlockChain.withGenesis
      val invalidBlock = Block.forge()

      emptyNode ! bc.blocks.values.last
      emptyNode ! invalidBlock
      emptyNode ! GetBlockChain
      expectMsg(bc)
    }

    "not accept invalid by id block" in {
      val emptyNode = system.actorOf(BlockChainNodeActor.props(BlockChain.empty, Seq.empty))

      val bc = BlockChain.withGenesis
      val invalidBlock = Block.forge().copy(id = "123")

      emptyNode ! bc.blocks.values.last
      emptyNode ! invalidBlock
      emptyNode ! GetBlockChain
      expectMsg(bc)
    }

    "not accept invalid by score block" in {
      val emptyNode = system.actorOf(BlockChainNodeActor.props(BlockChain.empty, Seq.empty))

      val bc = BlockChain.withGenesis
      val invalidBlock = Block.forge().copy(score = -1)

      emptyNode ! bc.blocks.values.last
      emptyNode ! invalidBlock
      emptyNode ! GetBlockChain
      expectMsg(bc)
    }

    "not accept invalid by parent block" in {
      val emptyNode = system.actorOf(BlockChainNodeActor.props(BlockChain.empty, Seq.empty))

      val bc = BlockChain.withGenesis
      val invalidBlock = Block.forge().copy(parentId = Some("1234"))

      emptyNode ! bc.blocks.values.last
      emptyNode ! invalidBlock
      emptyNode ! GetBlockChain
      expectMsg(bc)
    }

    "connects to a known node" in {
      val blockChainWithGenesis = BlockChain.withGenesis
      val nodeWithGenesisBlock = system.actorOf(BlockChainNodeActor.props(blockChainWithGenesis, Seq.empty))
      val emptyNode = system.actorOf(BlockChainNodeActor.props(BlockChain.empty, Seq(nodeWithGenesisBlock)))

      emptyNode ! GetConnectedPeers
      expectMsg(Seq(nodeWithGenesisBlock))

      nodeWithGenesisBlock ! GetConnectedPeers
      expectMsg(Seq(emptyNode))
    }

    "synchronize with a known node" in {
      val blockChainWithGenesis = BlockChain.withGenesis
      val nodeWithGenesisBlock = system.actorOf(BlockChainNodeActor.props(blockChainWithGenesis, Seq.empty))
      val emptyNode = system.actorOf(BlockChainNodeActor.props(BlockChain.empty, Seq(nodeWithGenesisBlock)))

      emptyNode ! GetBlockChain

      expectMsg(blockChainWithGenesis)
    }

    "connects with new outgoing connected node" in {
      val blockChainWithGenesis = BlockChain.withGenesis
      val nodeWithGenesisBlock = system.actorOf(BlockChainNodeActor.props(blockChainWithGenesis, Seq.empty))
      val emptyNode = system.actorOf(BlockChainNodeActor.props(BlockChain.empty, Seq.empty))

      emptyNode ! ConnectTo(nodeWithGenesisBlock)

      Thread.sleep(1000)

      emptyNode ! GetConnectedPeers
      expectMsg(Seq(nodeWithGenesisBlock))
    }

    "synchronize with new outgoing connected node" in {
      val blockChainWithGenesis = BlockChain.withGenesis
      val nodeWithGenesisBlock = system.actorOf(BlockChainNodeActor.props(blockChainWithGenesis, Seq.empty))
      val emptyNode = system.actorOf(BlockChainNodeActor.props(BlockChain.empty, Seq.empty))

      emptyNode ! ConnectTo(nodeWithGenesisBlock)

      Thread.sleep(1000)

      emptyNode ! GetBlockChain
      expectMsg(blockChainWithGenesis)
    }

    "connects with new incoming connected node" in {
      val blockChainWithGenesis = BlockChain.withGenesis
      val nodeWithGenesisBlock = system.actorOf(BlockChainNodeActor.props(blockChainWithGenesis, Seq.empty))
      val emptyNode = system.actorOf(BlockChainNodeActor.props(BlockChain.empty, Seq.empty))

      nodeWithGenesisBlock ! ConnectTo(emptyNode)

      Thread.sleep(1000)

      emptyNode ! GetConnectedPeers
      expectMsg(Seq(nodeWithGenesisBlock))
    }

    "synchronize with new incoming connected node" in {
      val blockChainWithGenesis = BlockChain.withGenesis
      val nodeWithGenesisBlock = system.actorOf(BlockChainNodeActor.props(blockChainWithGenesis, Seq.empty))
      val emptyNode = system.actorOf(BlockChainNodeActor.props(BlockChain.empty, Seq.empty))

      nodeWithGenesisBlock ! ConnectTo(emptyNode)

      Thread.sleep(1000)

      emptyNode ! GetBlockChain
      expectMsg(blockChainWithGenesis)
    }

    "not synchronize with new node with different genesis block" in {
      val blockChainWithGenesis = BlockChain.withGenesis
      val blockChainWithGenesis2 = BlockChain.withGenesis

      val nodeWithGenesisBlock = system.actorOf(BlockChainNodeActor.props(blockChainWithGenesis, Seq.empty))
      val nodeWithGenesisBlock2 = system.actorOf(BlockChainNodeActor.props(blockChainWithGenesis2, Seq.empty))

      nodeWithGenesisBlock2 ! ConnectTo(nodeWithGenesisBlock)
      Thread.sleep(1000)
      nodeWithGenesisBlock ! GetBlockChain
      expectMsg(blockChainWithGenesis)

      nodeWithGenesisBlock2 ! GetBlockChain
      expectMsg(blockChainWithGenesis2)
    }

    "not connects with new node with different genesis block" in {
      val blockChainWithGenesis = BlockChain.withGenesis
      val blockChainWithGenesis2 = BlockChain.withGenesis

      val nodeWithGenesisBlock = system.actorOf(BlockChainNodeActor.props(blockChainWithGenesis, Seq.empty))
      val nodeWithGenesisBlock2 = system.actorOf(BlockChainNodeActor.props(blockChainWithGenesis2, Seq.empty))

      nodeWithGenesisBlock2 ! ConnectTo(nodeWithGenesisBlock)
      Thread.sleep(500)
      nodeWithGenesisBlock ! GetConnectedPeers
      expectMsg(Seq.empty[ActorRef])

      nodeWithGenesisBlock2 ! GetConnectedPeers
      expectMsg(Seq.empty[ActorRef])
    }

    "synchronize network with the best chain in star topology" in {
      val blockChainWithGenesis = BlockChain.withGenesis

      val blockChainWithBestFork = blockChainWithGenesis.append(Block(blockChainWithGenesis.lastBlockId, Block.generateId, 500)).get
      val someOtherForks = Seq.fill(5)(blockChainWithGenesis.append(Block.forge(blockChainWithGenesis.lastBlockId)).get)

      val allForks = someOtherForks :+ blockChainWithBestFork

      val nodesWithDifferentBlockChains = allForks.map(bc =>
        system.actorOf(BlockChainNodeActor.props(bc, Seq.empty)))

      val emptyNode = system.actorOf(BlockChainNodeActor.props(BlockChain.empty, Seq.empty))

      nodesWithDifferentBlockChains.foreach(node => emptyNode ! ConnectTo(node))

      Thread.sleep(1000)

      val allNodes = emptyNode +: nodesWithDifferentBlockChains

      allNodes.foreach(node => {
        node ! GetBlockChain
        expectMsg(blockChainWithBestFork)
      })
    }

    "synchronize network with the best chain in chain topology" in {
      val blockChainWithGenesis = BlockChain.withGenesis

      val blockChainWithBestFork = blockChainWithGenesis.append(Block(blockChainWithGenesis.lastBlockId, Block.generateId, 500)).get
      val someOtherForks = Seq.fill(5)(blockChainWithGenesis.append(Block.forge(blockChainWithGenesis.lastBlockId)).get)

      val allForks = someOtherForks :+ blockChainWithBestFork

      val nodesWithDifferentBlockChains = allForks.map(bc =>
        system.actorOf(BlockChainNodeActor.props(bc, Seq.empty)))

      val emptyNode = system.actorOf(BlockChainNodeActor.props(BlockChain.empty, Seq.empty))

      val allNodes = emptyNode +: nodesWithDifferentBlockChains

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