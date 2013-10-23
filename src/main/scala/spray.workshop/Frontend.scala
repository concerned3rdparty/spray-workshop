package spray.workshop

import akka.actor._
import spray.routing._
import spray.routing.authentication.{ UserPass, BasicAuth }
import scala.concurrent.{ Future, ExecutionContext }
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import spray.json.DefaultJsonProtocol
import spray.http.Uri

class FrontendActor extends Actor with FrontendService {
  implicit def actorRefFactory = context
  implicit def executionContext: ExecutionContext = context.dispatcher

  val usersService: ActorRef = context.actorOf(Props(classOf[UsersService]))
  usersService ! UsersService.CreateUser("alan")
  usersService ! UsersService.CreateUser("ada")

  def receive = runRoute(route ~ staticRoute)

  def staticRoute =
    path("index.html")(getFromResource("web/index.html")) ~ getFromResourceDirectory("web")
}

trait TestJs {
  case class Fruit(name: String)

  object FruitProtocol {
    import spray.json.DefaultJsonProtocol._
    implicit val format = jsonFormat1(Fruit)
  }

  val fruits = Fruit("apple") :: Fruit("orange") :: Fruit("banana") :: Nil
}

trait FrontendService extends HttpService with UserJsonProtocol with TestJs {
  import DefaultJsonProtocol._
  import spray.httpx.SprayJsonSupport._

  implicit def executionContext: ExecutionContext

  def usersService: ActorRef

  implicit val timeout = Timeout(2.seconds)

  // format: OFF
  def route =
    // public
    path("")(
      get (
        complete("Hello world!")
      )
    ) ~
    pathPrefix("api")(
      path("users")(
        get(
          complete(
            (usersService ? UsersService.GetUsers) map {
              case UsersService.Users(users) => users
            }
          )
        )
      ) ~
        path("fruits") (
          get {
            import FruitProtocol._
            complete(fruits)
          }
        )
    ) ~
    pathPrefix("home")(
      authenticate(BasicAuth(authenticateUser _, "spray-workshop"))(user =>
        path("")(
          complete(s"Hello ${user.id}")
        )
      )
    )

  // format: ON
  def resolveUserId(id: String): UserId = ???
  def apiUriFor(user: UserId): Uri = Uri("/api/user/" + user.id)
  def htmlUriFor(user: UserId): Uri = Uri("/" + user.id)

  def authenticateUser(user: Option[UserPass]): Future[Option[UserId]] =
    user match {
      case Some(user) => (usersService ? UsersService.AuthenticateUser(user)).mapTo[Option[UserId]]
      case None       => Future.successful(None)
    }
}

