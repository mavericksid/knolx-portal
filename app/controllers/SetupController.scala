package controllers

import javax.inject.{Inject, Singleton}

import models.UsersRepository
import play.api.mvc.{Action, AnyContent}
import utilities.{DateTimeUtility, PasswordUtility}

import scala.concurrent.Future

@Singleton
class SetupController @Inject()(usersRepository: UsersRepository,
                                controllerComponents: KnolxControllerComponents
                               ) extends KnolxAbstractController(controllerComponents) {

  val usersSource = scala.io.Source.fromFile("/home/ubuntu/users.csv")
  val users = usersSource.getLines.toList
  usersSource.close()

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

      Ok(inserted)
    }
  }

}
