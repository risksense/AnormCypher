package org.anormcypher

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

/** CypherBatch case class
  */
case class CypherBatch(batch: Seq[CypherTransaction]) {

  /** Executes all transactions as a batch.
    *
    * @return True if all transactions in this batch succeeded, false otherwise
    */
  def execute()
      (implicit connection: Neo4jREST, ec: ExecutionContext): Boolean = {
    var retVal = true // scalastyle:ignore var.local
    try {
      val optionalCommitPath = executeTransaction(batch.head)
      optionalCommitPath match {
        case Some(commitPath) =>
          val transId = CypherBatch.getTransactionId(commitPath)
          batch.tail.map { t => executeTransaction(t, transId) }
          retVal = connection.commitTransaction(transId)
        case None => retVal = false
      }
    } catch {
      case e: Exception => retVal = false
    }
    retVal
  }

  /** Executes a transaction on an existing endpoint
    *
    * This method executes a Cypher query as a transaction that has already
    * been created and is open.
    *
    * @param trans a [[CypherTransaction]] object
    * @param transId the identifier of an existing transaction
    * @return an Option containing a commit URL if the transaction is successful,
    * None otherwise.
    */
  private def executeTransaction(
      trans: CypherTransaction,
      transId: String = "")
      (implicit connection: Neo4jREST,
        ec: ExecutionContext): Option[String] = {
    try {
      Await.result(connection.sendTransaction(trans, "transaction" + transId),
                   trans.timeout)
    } catch {
      case e: Exception => throw new RuntimeException(e.getMessage)
    }
  }
}

/** CypherBatch companion object
  */
object CypherBatch {

  /** Extracts a transaction id from a commit path returned by Neo4j server.
    *
    * Neo4j returns a 'commit' value inside the response. This contains the
    * commit path like 'http://localhost:7474/db/data/transaction/16/commit'.
    * The transaction id, for e.g. 16, is returned by this method.
    *
    * @param commitPath the transaction commit path
    * @return transaction id as a String
    */
  def getTransactionId(commitPath: String): String = {
    val dropLength = "/commit".length()
    val init = commitPath.dropRight(dropLength + 1)
    val chopPos = init.lastIndexOf('/')
    init.substring(chopPos)
  }
}
