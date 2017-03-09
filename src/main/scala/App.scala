import fs2._
import scala.concurrent.duration._
import scala.annotation.tailrec
import scala.util.Random

import org.http4s.client.blaze._
import org.http4s.circe._
import org.http4s.dsl._
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._

object TwitterClient {
  import com.danielasfregola.twitter4s.TwitterRestClient

  val client = TwitterRestClient()

  def tweet(url: String, title: String, hashtag: String) = {
    client.createTweet(status = s"$hashtag $url $title")
  }
}
object TweetAPaperApp extends App {
  implicit val strategy = Strategy.fromFixedDaemonPool(4)
  implicit val scheduler = Scheduler.fromFixedDaemonPool(4)
  val freq = sys.env.get("FREQ").map(_.toInt).getOrElse(6)

  lazy val list = paperList("https://api.github.com/repos/papers-we-love/papers-we-love/contents/")
  val urlStream: Stream[Task, Option[(String, String)]] = time.awakeEvery[Task](freq.second).evalMap { _ =>
    Task.delay{
      val paperCat = randomPaperCategory(list)
      randomPaper(paperCat)
    }
  }
  urlStream.observe(sink2[Task,Option[(String,String)]]("#WinterEvent2017")).run.unsafeRun

  case class GHFile(name: String, url: String, download_url: Option[String])

  def fetch(url: String) = PooledHttp1Client().expect[String](url).run
  def paperList(at: String) = PooledHttp1Client().expect(at)(jsonOf[List[GHFile]]).run

  def randomPaperCategory(list: List[GHFile]) = {
    @tailrec
    def go(list: List[GHFile]): String = list(Random.nextInt(list.size)) match {
      case GHFile(name, url, Some(download_url)) => download_url
      case GHFile(name, url, None) => go(paperList(url))
    }
    go(list.filterNot(_.download_url.isDefined))
  }

  def randomPaper(readmeUrl: String): Option[(String,String)] = {
    val pattern = """.*\[(.+)\]\((.+.pdf)\).*""".r
    val list: List[(String,String)] = (readmeUrl match {
      case url if url.toLowerCase contains "readme.md" => fetch(readmeUrl).split("\n").toList map {
        case pattern(title,url) => Some((title,url))
        case _ => None
      }
      case _ => List(None)
    }).flatten

    Random.shuffle(list).headOption
  }

  def sink2[F[_],A](prefix: String):Sink[F, A] = in => in.map(x =>
    x match {
      case Some((title,url)) =>
        println(s"${Thread.currentThread().getName} $prefix $title - $url")
        TwitterClient.tweet(url.toString,title.toString,prefix.toString)
      case None =>
        println(s"${Thread.currentThread().getName} $prefix empty")
    })

}

