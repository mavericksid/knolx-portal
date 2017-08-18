package controllers

import javax.inject.{Named, Inject, Singleton}

import actors.EmailActor
import akka.actor.ActorRef
import models.UsersRepository
import play.api.mvc.{Action, AnyContent}
import utilities.PasswordUtility

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class SetupController @Inject()(usersRepository: UsersRepository,
                                controllerComponents: KnolxControllerComponents,
                                @Named("EmailManager") emailManager: ActorRef
                               ) extends KnolxAbstractController(controllerComponents) {

  val usersSource = scala.io.Source.fromFile("/home/ubuntu/users.csv")
  val users = usersSource.getLines.toList
  usersSource.close()

  def sendCredentials: Action[AnyContent] = adminAction { implicit request =>
    users foreach { user =>
      val usernamePassword = user.split(",")
      val (username, password) = (usernamePassword.head, usernamePassword(1))

      val body =
        s"""Knolx Portal: http://knolx.knoldus.com
            |Username: $username
            |Password: $password
        """.stripMargin

      emailManager ! EmailActor.SendEmail(List(username), "support@knoldus.com", "Knolx Portal Credentials", body)
    }

    Ok("sent!")
  }

  def addUsers(): Action[AnyContent] = adminAction.async { implicit request =>
    val insertResult = users map { user =>
      val usernamePassword = user.split(",")
      val (username, password) = (usernamePassword.head, usernamePassword(1))

      usersRepository
        .insert(
          models.UserInfo(
            username,
            PasswordUtility.encrypt(password),
            PasswordUtility.BCrypt,
            active = true,
            admin = false))
    }

    val eventualResults = Future.sequence(insertResult)

    eventualResults map { result =>
      val inserted = result.forall(_.ok)

      Ok(inserted.toString)
    }
  }

}
