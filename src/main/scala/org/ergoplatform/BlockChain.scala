package org.ergoplatform

import scala.collection.immutable.ListMap
import scala.util.{Failure, Success, Try}

object BlockChain {
  def withGenesis: BlockChain = empty.append(Block.forge()).get
  val empty = BlockChain()
}

case class BlockChain private(blocks: ListMap[String, Block] = ListMap.empty) extends Iterable[Block] {

  def lastBlockId: Option[String] = blocks.lastOption.map(_._2).map(_.id)

  def height: Int = blocks.size

  def score: Long = iterator.map(_.score).sum

  def contains(b: Block): Boolean = iterator.contains(b)

  def append(b: Block): Try[BlockChain] = {
    Block.isValid(b, this) match {
      case Left(error) =>
        Failure(new IllegalArgumentException(s"Invalid block $b for chain $this: ${error.msg}"))
      case Right(b) =>
        Success(new BlockChain(blocks + (b.id -> b)))
    }
  }

  override def iterator: Iterator[Block] = blocks.valuesIterator

  override def toString: String = s"BlockChain [height: $height, score: $score]:" + iterator.mkString("\n\t", "\n\t", "\n")
}