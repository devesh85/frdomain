package frdomain.ch7
package streams

import java.util.Date
import scala.concurrent.duration._
import scala.concurrent.{ Future, ExecutionContext }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.immutable._

import scalaz._
import Scalaz._

import common._

sealed trait TransactionType
case object Debit extends TransactionType
case object Credit extends TransactionType

object TransactionType {
  def apply(s: String) = s.toLowerCase match {
    case "d" => Some(Debit)
    case "c" => Some(Credit)
    case _ => None
  }
}

case class Transaction(id: String, accountNo: String, debitCredit: TransactionType, amount: Amount, date: Date = today)

object Transaction {
  def apply(fields: Array[String]): Option[Transaction] = {
    this(fields(0), fields(1), fields(2), BigDecimal(fields(3)))  // @todo: exception handling
  }

  def apply(id: String, accountNo: String, t: String, amount: Amount): Option[Transaction] = TransactionType(t) match {
    case None    => None
    case Some(d) => Some(Transaction(id, accountNo, d, amount, today))
  }

  implicit val TransactionMonoid = new Monoid[Transaction] {
    val zero = Transaction("", "", Debit, 0)
    def append(i: Transaction, j: => Transaction) = {
      val f = if (i.debitCredit == Debit) -i.amount else i.amount
      val s = if (j.debitCredit == Debit) -j.amount else j.amount
      val sum = f + s
      val id = util.Random.nextInt(Integer.MAX_VALUE).toString
      if (sum < 0) Transaction(id, j.accountNo, Debit, -sum) else Transaction(id, j.accountNo, Credit, sum)
    }
  }
}

case class Balance(amount: Amount, debitCredit: TransactionType)

object Balance {
  implicit val BalanceMonoid = new Monoid[Balance] {
    val zero = Balance(0, Debit)
    def append(i: Balance, j: => Balance) = (i.debitCredit, j.debitCredit) match {
      case (Debit, Debit)                         => Balance(i.amount + j.amount, Debit)
      case (Credit, Credit)                       => Balance(i.amount + j.amount, Credit)
      case (Debit, Credit) if i.amount > j.amount => Balance(i.amount - j.amount, Debit)
      case (Debit, Credit)                        => Balance(j.amount - i.amount, Credit)
      case (Credit, Debit) if i.amount > j.amount => Balance(i.amount - j.amount, Credit)
      case (Credit, Debit)                        => Balance(j.amount - i.amount, Debit)
    }
  }
}

object LogSummaryBalance

trait AccountRepository {
  def query(no: String): Option[Account]
}

object AccountRepository extends AccountRepository {
  val m = Map("a-1" -> Account("a-1", "dg", today.some),
              "a-2" -> Account("a-2", "gh", today.some),
              "a-3" -> Account("a-3", "tr", today.some)
          )
  def query(no: String) = m.get(no)
}

trait OnlineService {
  def allAccounts(implicit ec: ExecutionContext): Future[Seq[String]] = Future {
    Seq("a-1", "a-2", "a-3")
  }

  def queryAccount(no: String, repo: AccountRepository) = 
    repo.query(no).getOrElse { throw new RuntimeException("Invalid account number") }

  val txns =
    Seq(
      Transaction("t-1", "a-1", Debit, 1000),
      Transaction("t-2", "a-2", Debit, 1000),
      Transaction("t-3", "a-3", Credit, 1000),
      Transaction("t-4", "a-1", Credit, 1000),
      Transaction("t-5", "a-1", Debit, 1000),
      Transaction("t-6", "a-2", Debit, 1000),
      Transaction("t-7", "a-3", Credit, 1000),
      Transaction("t-8", "a-3", Debit, 1000),
      Transaction("t-9", "a-2", Credit, 1000),
      Transaction("t-10", "a-2", Debit, 1000),
      Transaction("t-11", "a-1", Credit, 1000),
      Transaction("t-12", "a-3", Debit, 1000)
    )

  def getBankingTransactions(a: Account) = txns.filter(_.accountNo == a.no)
  def getSettlementTransactions(a: Account) = txns.filter(_.accountNo == a.no)
  def validate(t: Transaction) = t

  def allTransactions(implicit ec: ExecutionContext): Future[Seq[Transaction]] = Future { txns }

}

object OnlineService extends OnlineService
