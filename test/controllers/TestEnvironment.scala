package controllers

import java.util.concurrent.TimeoutException

import actors.SessionsScheduler._
import actors.{EmailManager, ConfiguredEmailActor, EmailActor}
import akka.actor._
import com.google.inject.name.Names
import com.google.inject.{AbstractModule, Module}
import com.typesafe.config.ConfigFactory
import helpers.BeforeAllAfterAll
import models.UsersRepository
import org.apache.commons.mail.EmailException
import org.specs2.mock.Mockito
import org.specs2.mutable.SpecificationLike
import play.api.http._
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.libs.streams.Accumulator
import play.api.mvc.{BodyParser, _}
import play.api.test._
import play.api.{Application, Configuration}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

trait TestEnvironment extends SpecificationLike with BeforeAllAfterAll with Mockito {

  val actorSystem: ActorSystem = ActorSystem("TestEnvironment")

  val usersRepository = mock[UsersRepository]
  val config = Configuration(ConfigFactory.load("application.conf"))
  val knolxControllerComponent = TestHelpers.stubControllerComponents

  override def afterAll(): Unit = {
    shutdownActorSystem(actorSystem)
  }

  protected def fakeApp: Application = {
    val sessionsScheduler = actorSystem.actorOf(Props(new DummySessionsScheduler))

    val testModule = Option(new AbstractModule with AkkaGuiceSupport {
      override def configure(): Unit = {
        bind(classOf[ActorRef])
          .annotatedWith(Names.named("SessionsScheduler"))
          .toInstance(sessionsScheduler)

        bindActorFactory[TestEmailActor, ConfiguredEmailActor.Factory]
        bindActor[EmailManager]("EmailManager")

        bind(classOf[KnolxControllerComponents])
          .toInstance(knolxControllerComponent)
      }
    })

    new GuiceApplicationBuilder()
      .overrides(testModule.map(GuiceableModule.guiceable).toSeq: _*)
      .disable[Module]
      .build
  }


  protected def shutdownActorSystem(actorSystem: ActorSystem,
                                    duration: Duration = 10.seconds,
                                    verifySystemShutdown: Boolean = false): Unit = {
    actorSystem.terminate()

    try Await.ready(actorSystem.whenTerminated, duration) catch {
      case _: TimeoutException ⇒
        val msg = "Failed to stop [%s] within [%s]".format(actorSystem.name, duration)

        if (verifySystemShutdown) {
          throw new RuntimeException(msg)
        } else {
          actorSystem.log.warning(msg)
        }
    }
  }

  object TestHelpers extends PlayRunners
    with HeaderNames
    with Status
    with MimeTypes
    with HttpProtocol
    with DefaultAwaitTimeout
    with ResultExtractors
    with Writeables
    with EssentialActionCaller
    with RouteInvokers
    with FutureAwaits
    with TestStubControllerComponentsFactory


  trait TestStubControllerComponentsFactory extends StubPlayBodyParsersFactory with StubBodyParserFactory with StubMessagesFactory {

    override def stubBodyParser[T](content: T = AnyContentAsEmpty): BodyParser[T] = {
      BodyParser(_ => Accumulator.done(Right(content)))
    }

    def stubControllerComponents: KnolxControllerComponents = {
      val bodyParser = stubBodyParser(AnyContentAsEmpty)
      val executionContext = ExecutionContext.global

      DefaultKnolxControllerComponents(
        DefaultActionBuilder(bodyParser)(executionContext),
        UserActionBuilder(bodyParser, usersRepository, config)(executionContext),
        AdminActionBuilder(bodyParser, usersRepository, config)(executionContext),
        stubPlayBodyParsers(NoMaterializer),
        stubMessagesApi(),
        stubLangs(),
        new DefaultFileMimeTypes(FileMimeTypesConfiguration()),
        executionContext)
    }

  }

}

class DummySessionsScheduler extends Actor {

  def receive: Receive = {
    case RefreshSessionsSchedulers         => sender ! ScheduledSessionsRefreshed
    case GetScheduledSessions              => sender ! ScheduledSessions(List.empty)
    case CancelScheduledSession(sessionId) => sender ! true
    case ScheduleSession(sessionId)        => sender ! true
  }

}

class TestEmailActor extends Actor {

  def receive: Receive = {
    case EmailActor.SendEmail(_, _, subject, _) if subject == "crash" => throw new EmailException
    case request: EmailActor.SendEmail                                => sender ! request
  }

}
