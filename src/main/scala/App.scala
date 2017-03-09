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

  lazy val client = TwitterRestClient()

  def format(url: String, title: String, hashtag: String) = {
    lazy val maxLength = sys.env.get("MAX_LENGTH").map(_.toInt).getOrElse(80)
    val templates =
        s"❤ $hashtag: $title".take(maxLength) + s" $url" ::
        s"A good one for $hashtag: $title".take(maxLength) + s" $url" ::
        s"Fantastic! $hashtag $title".take(maxLength) + s" $url" ::
        s".@hascurry1900 have a look $hashtag $title".take(maxLength) + s" $url" ::
        s".@hascurry1900 would you consider? $hashtag".take(maxLength) + s" $url" ::
        s"What a great 📃! $hashtag".take(maxLength) + s" $url" ::
        s"Really intrigued by $title $hashtag".take(maxLength) + s" $url" ::
        s"Certainly worth reading $hashtag".take(maxLength) + s" $url" ::
        s"Spent some time with $title. $hashtag".take(maxLength) + s" $url" ::
        s".@hascurry1900 have you read the $title?".take(maxLength) ::
        s"Some quality reading $hashtag: $title".take(maxLength) + s" $url" ::
        s"💙 $hashtag $title".take(maxLength) + s" $url" ::
        s"Oh! $hashtag $title".take(maxLength) + s" $url" ::
        s".@hascurry1900 💝 $hashtag $title".take(maxLength) + s" $url" ::
        s"💚 $hashtag $title".take(maxLength) + s" $url" ::
        s"💟 $hashtag $title".take(maxLength) + s" $url" ::
        s"📄 $hashtag $title".take(maxLength) + s" $url" ::
        s"Great read! $hashtag $title".take(maxLength) + s" $url" ::
        s"$hashtag SQL, Lisp, and Haskell are the only programming languages that I've seen where one spends more time thinking than typing.".take(130) ::
        s"$hashtag A parser for things is a function from strings to lists of pairs of things and strings".take(130) ::
        s"Mathematical logic... has much the same relation to the analysis and criticism of thought as geometry does to the science of space.".take(140) ::
        s"$title $hashtag".take(maxLength) + s" $url" ::
        Nil
    templates(Random.nextInt(templates.size))
  }

  def tweet(url: String, title: String, hashtag: String) = {
    client.createTweet(status = format(url, title, hashtag))
  }
}
case class GHFile(name: String, url: String, download_url: Option[String])
object TweetAPaperApp extends App {
  implicit val strategy = Strategy.fromFixedDaemonPool(4)
  implicit val scheduler = Scheduler.fromFixedDaemonPool(4)
  lazy val freq = sys.env.get("FREQ").map(_.toInt).getOrElse(30)
  lazy val hashtag = sys.env.get("HASHTAG").map(_.toString).getOrElse("#WinterEvent2017")

  lazy val list = paperList("https://api.github.com/repos/papers-we-love/papers-we-love/contents/")
  lazy val urlStream: Stream[Task, Option[(String, String)]] = time.awakeEvery[Task](freq.second).evalMap { _ =>
    Task.delay{
      getOnePaper(list)
    }
  }
  urlStream.observe(sink2[Task,Option[(String,String)]](hashtag)).run.unsafeRun

  def fetch(url: String) = PooledHttp1Client().expect[String](url).run
  def paperList(at: String) = PooledHttp1Client().expect(at)(jsonOf[List[GHFile]]).run

  @tailrec
  def getOnePaper(list: List[GHFile]): Option[(String,String)] = {
    val paperCat = randomPaperCategory(list)
    randomPaper(paperCat) match {
      case Some(paper) => Some(paper)
      case None => getOnePaper(list)
    }
  }
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
        println(s"${Thread.currentThread().getName} ${TwitterClient.format(url.toString,prefix.toString,title.toString)}")
        TwitterClient.tweet(url.toString,title.toString,prefix.toString)
      case None =>
        println(s"${Thread.currentThread().getName} $prefix empty")
    })

}

