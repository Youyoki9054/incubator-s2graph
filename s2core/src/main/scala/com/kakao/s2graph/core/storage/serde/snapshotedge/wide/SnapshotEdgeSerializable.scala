package com.kakao.s2graph.core.storage.serde.snapshotedge.wide

import com.kakao.s2graph.core.SnapshotEdge
import com.kakao.s2graph.core.mysqls.LabelIndex
import com.kakao.s2graph.core.storage.{SKeyValue, Serializable, StorageSerializable}
import com.kakao.s2graph.core.types.VertexId
import org.apache.hadoop.hbase.util.Bytes


/**
 * this class serialize
 * @param snapshotEdge
 */
class SnapshotEdgeSerializable(snapshotEdge: SnapshotEdge) extends Serializable[SnapshotEdge] {
  import StorageSerializable._

  val label = snapshotEdge.label
  val table = label.hbaseTableName.getBytes()
  val cf = Serializable.edgeCf

  def statusCodeWithOp(statusCode: Byte, op: Byte): Array[Byte] = {
    val byte = (((statusCode << 4) | op).toByte)
    Array.fill(1)(byte.toByte)
  }
  def valueBytes() = Bytes.add(statusCodeWithOp(snapshotEdge.statusCode, snapshotEdge.op),
    propsToKeyValuesWithTs(snapshotEdge.props.toList))

  override def toKeyValues: Seq[SKeyValue] = {
    val srcIdBytes = VertexId.toSourceVertexId(snapshotEdge.srcVertex.id).bytes
    val labelWithDirBytes = snapshotEdge.labelWithDir.bytes
    val labelIndexSeqWithIsInvertedBytes = labelOrderSeqWithIsInverted(LabelIndex.DefaultSeq, isInverted = true)

    val row = Bytes.add(srcIdBytes, labelWithDirBytes, labelIndexSeqWithIsInvertedBytes)
    val tgtIdBytes = VertexId.toTargetVertexId(snapshotEdge.tgtVertex.id).bytes

    val qualifier = tgtIdBytes

    val value = snapshotEdge.pendingEdgeOpt match {
      case None => valueBytes()
      case Some(pendingEdge) =>
        val opBytes = statusCodeWithOp(pendingEdge.statusCode, pendingEdge.op)
        val versionBytes = Array.empty[Byte]
        val propsBytes = propsToKeyValuesWithTs(pendingEdge.propsWithTs.toSeq)
        val lockBytes = Bytes.toBytes(pendingEdge.lockTs.get)
        Bytes.add(Bytes.add(valueBytes(), opBytes, versionBytes), Bytes.add(propsBytes, lockBytes))
    }
    val kv = SKeyValue(table, row, cf, qualifier, value, snapshotEdge.version)
    Seq(kv)
  }
}