/**
 * Copyright (C) 2016 Typesafe Inc. <http://www.typesafe.com>
 */
package akka.persistence.dynamodb.journal

import akka.testkit._
import org.scalactic.ConversionCheckedTripleEquals
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import akka.actor.ActorSystem
import akka.persistence._
import akka.persistence.JournalProtocol._
import java.util.UUID

class DeletionSpec extends TestKit(ActorSystem("FailureReportingSpec"))
    with ImplicitSender
    with WordSpecLike
    with BeforeAndAfterAll
    with Matchers
    with ScalaFutures
    with ConversionCheckedTripleEquals
    with DynamoDBUtils {

  override def beforeAll(): Unit = ensureJournalTableExists()
  override def afterAll(): Unit = {
    system.terminate().futureValue
    client.shutdown()
  }

  override val persistenceId = "DeletionSpec"
  val journal = Persistence(system).journalFor("")

  "DynamoDB Journal (Deletion)" must {

    "1 purge events" in {
      journal ! Purge(persistenceId, testActor)
      expectMsg(Purged(persistenceId))
      journal ! ListAll(persistenceId, testActor)
      expectMsg(ListAllResult(persistenceId, Set.empty, Set.empty, Nil))
    }

    "2 store events" in {
      val msgs = (1 to 149).map(i => AtomicWrite(persistentRepr(s"a-$i")))
      journal ! WriteMessages(msgs, testActor, 1)
      expectMsg(WriteMessagesSuccessful)
      (1 to 149) foreach (i => expectMsgType[WriteMessageSuccess].persistent.sequenceNr should ===(i))
      journal ! ListAll(persistenceId, testActor)
      expectMsg(ListAllResult(persistenceId, Set.empty, Set(100L), (1L to 149)))

      val more = AtomicWrite((150 to 200).map(i => persistentRepr("b-$i")))
      journal ! WriteMessages(more :: Nil, testActor, 1)
      expectMsg(WriteMessagesSuccessful)
      (150 to 200) foreach (i => expectMsgType[WriteMessageSuccess].persistent.sequenceNr should ===(i))
      journal ! ListAll(persistenceId, testActor)
      expectMsg(ListAllResult(persistenceId, Set.empty, Set(100L, 200L), (1L to 200)))
    }

    "3 delete some events" in {
      journal ! DeleteMessagesTo(persistenceId, 5L, testActor)
      expectMsg(DeleteMessagesSuccess(5L))
      journal ! ListAll(persistenceId, testActor)
      expectMsg(ListAllResult(persistenceId, Set(6L), Set(100L, 200L), (6L to 200)))
    }

    "4 delete no events" in {
      journal ! DeleteMessagesTo(persistenceId, 3L, testActor)
      expectMsg(DeleteMessagesSuccess(3L))
      journal ! ListAll(persistenceId, testActor)
      expectMsg(ListAllResult(persistenceId, Set(6L), Set(100L, 200L), (6L to 200)))
    }

    "5 delete all events" in {
      journal ! DeleteMessagesTo(persistenceId, 210L, testActor)
      expectMsg(DeleteMessagesSuccess(210L))
      journal ! ListAll(persistenceId, testActor)
      expectMsg(ListAllResult(persistenceId, Set(6L, 201L), Set(100L, 200L), Nil))
    }

    "6 purge events" in {
      journal ! Purge(persistenceId, testActor)
      expectMsg(Purged(persistenceId))
      journal ! ListAll(persistenceId, testActor)
      expectMsg(ListAllResult(persistenceId, Set.empty, Set.empty, Nil))
    }

  }

}