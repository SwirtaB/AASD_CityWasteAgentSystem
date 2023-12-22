package mission.impossibl.bots.http

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import mission.impossibl.bots.collector.Garbage
import mission.impossibl.bots.sink.{GarbagePacket, GarbagePacketRecord}
import spray.json.DefaultJsonProtocol

import java.util.UUID
import scala.concurrent.duration.FiniteDuration
import spray.json._

import java.util.concurrent.TimeUnit

final case class EnvironmentReply(
  sources: List[SourceStatus]
)

final case class SourceStatus(
  id: UUID,
  capacity: Int,
  location: (Int, Int),
  garbageLevel: Int,
  score: Int,
  isWaitingForCollection: Boolean,
  isWaitingForAuctionResult: Boolean
)

final case class SinkStatus(
  id: UUID,
  efficiency: Int,
  location: (Int, Int),
  capacity: Int,
  garbagePackets: List[GarbagePacket],
  totalReserved: Int
)

final case class CollectorStatus(
  id: UUID,
  capacity: Int,
  location: (Int, Int),
  garbageLevel: Int,
  visitedSources: List[SourcePathElem],
  futureSources: List[SourcePathElem],
  ongoingCollectionAuctions: Map[UUID, Garbage],
  disposalPoint: Option[(Int, Int)]
)

final case class SourcePathElem(
  location: (Int, Int),
  amount: Int,
  id: UUID
)
final case class OrchestratorStatus(
  id: UUID,
  auctionsInProgress: List[AuctionStatus]
)

final case class AuctionStatus(
  kind: String,
  id: UUID,
  expectedOffers: Int,
  receivedOffers: List[Either[(Int, Int), FiniteDuration]],
  pointDetails: PointDetails
)

sealed trait PointDetails
final case class CollectionPoint(
  amount: Int,
  location: (Int, Int),
  id: UUID
) extends PointDetails

final case class DisposalPoint(
  amount: Int,
  id: UUID
) extends PointDetails

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val uuidFormat: RootJsonFormat[UUID] = new RootJsonFormat[UUID] {
    override def read(json: JsValue): UUID = json match {
      case JsString(uuid) => UUID.fromString(uuid)
      case value          => throw new IllegalArgumentException("Cannot make uuid from" + value)
    }

    override def write(obj: UUID): JsValue = JsString(obj.toString)
  }

  implicit val durationFormat: RootJsonFormat[FiniteDuration] = new RootJsonFormat[FiniteDuration] {
    override def read(json: JsValue): FiniteDuration = json match {
      case JsNumber(time) => FiniteDuration(time.toLong, TimeUnit.SECONDS)
      case other          => throw new IllegalArgumentException(s"Cannot deserialize duration from " + other)
    }

    override def write(obj: FiniteDuration): JsValue = JsString(obj.toString())
  }

  implicit val dpf: RootJsonFormat[DisposalPoint]        = jsonFormat2(DisposalPoint.apply)
  implicit val gprf: RootJsonFormat[GarbagePacketRecord] = jsonFormat3(GarbagePacketRecord.apply)
  implicit val cpf: RootJsonFormat[CollectionPoint]      = jsonFormat3(CollectionPoint.apply)

  implicit val disposalPoint: RootJsonFormat[PointDetails] = new RootJsonFormat[PointDetails] {
    override def read(json: JsValue): PointDetails =
      if (json.asJsObject.fields.contains("location")) {
        cpf.read(json)
      } else {
        dpf.read(json)
      }

    override def write(obj: PointDetails): JsValue = obj match {
      case c: CollectionPoint => cpf.write(c)
      case d: DisposalPoint   => dpf.write(d)
    }
  }

  implicit val asf: RootJsonFormat[AuctionStatus]      = jsonFormat5(AuctionStatus.apply)
  implicit val osf: RootJsonFormat[OrchestratorStatus] = jsonFormat2(OrchestratorStatus.apply)
  implicit val spef: RootJsonFormat[SourcePathElem]    = jsonFormat3(SourcePathElem.apply)
  implicit val sosf: RootJsonFormat[SourceStatus]      = jsonFormat7(SourceStatus.apply)
  implicit val gpf: RootJsonFormat[GarbagePacket]      = jsonFormat2(GarbagePacket.apply)
  implicit val gf: RootJsonFormat[Garbage]             = jsonFormat2(Garbage.apply)
  implicit val csf: RootJsonFormat[CollectorStatus]    = jsonFormat8(CollectorStatus.apply)
  implicit val sisf: RootJsonFormat[SinkStatus]        = jsonFormat6(SinkStatus.apply)
  implicit val ssf: RootJsonFormat[EnvironmentReply]   = jsonFormat1(EnvironmentReply.apply)
}
