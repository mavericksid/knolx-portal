package controllers

import javax.inject._

import models.{UpdatedUserInfo, UsersRepository}
import play.api.Logger
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.{Action, AnyContent, Controller, Security}
import utilities.{EncryptionUtility, PasswordUtility}
// this is not an unused import contrary to what intellij suggests, do not optimize
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class UserInformation(email: String, password: String, confirmPassword: String)

case class LoginInformation(email: String, password: String)

case class UserEmailInformation(email: String)

case class ManageUserInfo(email: String, active: Boolean, _id: String)

case class UpdateUserInfo(id: String, active: Boolean, password: Option[String])

@Singleton
class UsersController @Inject()(val messagesApi: MessagesApi,
                                usersRepository: UsersRepository) extends Controller with SecuredImplicit with I18nSupport {
  val usersRepo: UsersRepository = usersRepository

  implicit val ManageUserInfoFormat: OFormat[ManageUserInfo] = Json.format[ManageUserInfo]

  val userForm = Form(
    mapping(
      "email" -> email,
      "password" -> nonEmptyText.verifying("Password must be at least 8 character long!", password => password.length >= 8),
      "confirmPassword" -> nonEmptyText.verifying("Password must be at least 8 character long!", password => password.length >= 8)
    )(UserInformation.apply)(UserInformation.unapply)
      verifying(
      "Password and confirm password miss match!", user => user.password.toLowerCase == user.confirmPassword.toLowerCase)
  )

  val loginForm = Form(
    mapping(
      "email" -> email,
      "password" -> nonEmptyText
    )(LoginInformation.apply)(LoginInformation.unapply)
  )

  val emailForm = Form(
    mapping(
      "email" -> email
    )(UserEmailInformation.apply)(UserEmailInformation.unapply)
  )

  val updateUserForm = Form(
    mapping(
      "id" -> nonEmptyText,
      "active" -> boolean,
      "password" -> optional(nonEmptyText.verifying("Password must be at least 8 character long!", password => password.length >= 8))
    )(UpdateUserInfo.apply)(UpdateUserInfo.unapply)
  )

  def register: Action[AnyContent] = Action { implicit request =>
    Ok(views.html.users.register(userForm))
  }

  def createUser: Action[AnyContent] = Action.async { implicit request =>
    userForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.error(s"Received a bad request for user registration $formWithErrors")
        Future.successful(BadRequest(views.html.users.register(formWithErrors)))
      },
      userInfo => {
        usersRepository
          .getByEmail(userInfo.email.toLowerCase)
          .flatMap(_.headOption.fold {
            usersRepository
              .insert(
                models.UserInfo(userInfo.email, PasswordUtility.encrypt(userInfo.password), PasswordUtility.BCrypt, active = true, admin = false))
              .map { result =>
                if (result.ok) {
                  Logger.info(s"User ${userInfo.email} successfully created")
                  Redirect(routes.HomeController.index())
                    .withSession(Security.username -> EncryptionUtility.encrypt(userInfo.email.toLowerCase))
                } else {
                  Logger.error(s"Something went wrong while creating a new user ${userInfo.email}")
                  Redirect(routes.HomeController.index()).flashing("message" -> "Something went wrong!")
                }
              }
          } { user =>
            Logger.info(s"User with email ${userInfo.email.toLowerCase} already exists")
            Future.successful(Redirect(routes.UsersController.register()).flashing("message" -> "User already exists!"))
          })
      }
    )
  }

  def login: Action[AnyContent] = Action { implicit request =>
    Ok(views.html.users.login(loginForm))
  }

  def loginUser: Action[AnyContent] = Action.async { implicit request =>
    loginForm.bindFromRequest.fold(
      formWithErrors => {
        Future.successful(BadRequest(views.html.users.login(formWithErrors)))
      },
      loginInfo => {
        usersRepository
          .getByEmail(loginInfo.email.toLowerCase)
          .map(_.headOption.fold {
            Logger.info(s"User ${loginInfo.email.toLowerCase} not found")
            Redirect(routes.HomeController.index()).flashing("message" -> "User not found!")
          } { user =>
            val admin = user.admin
            val password = user.password

            if (PasswordUtility.isPasswordValid(loginInfo.password, password)) {
              Logger.info(s"User ${loginInfo.email.toLowerCase} successfully logged in")
              if (admin) {
                Redirect(routes.HomeController.index())
                  .withSession(
                    Security.username -> EncryptionUtility.encrypt(loginInfo.email.toLowerCase),
                    "admin" -> EncryptionUtility.encrypt(EncryptionUtility.AdminKey))
                  .flashing("message" -> "Welcome back!")
              } else {
                Redirect(routes.HomeController.index())
                  .withSession(Security.username -> EncryptionUtility.encrypt(loginInfo.email.toLowerCase))
                  .flashing("message" -> "Welcome back!")
              }
            } else {
              Logger.info(s"Incorrect password for user ${loginInfo.email}")
              Unauthorized(views.html.users.login(loginForm.fill(loginInfo).withGlobalError("Invalid credentials!")))
            }
          })
      }
    )
  }

  def logout: Action[AnyContent] = Action { implicit request =>
    Redirect(routes.HomeController.index()).withNewSession
  }

  def manageUser: Action[AnyContent] = AdminAction { implicit request =>
    Ok(views.html.users.manageusers())
  }

  def searchUser: Action[AnyContent] = AdminAction.async { implicit request =>
    emailForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.error(s"Received a bad request for user manage $formWithErrors")
        Future.successful(BadRequest("Invalid email"))
      },
      userInfo => {
        usersRepository
          .getByEmail(userInfo.email.toLowerCase)
          .flatMap {
            case Some(user) =>
              val foundUser = ManageUserInfo(user.email, user.active, user._id.stringify)
              Future.successful(Ok(Json.toJson(foundUser).toString))
            case None =>
              Future.successful(NotFound("404! User not found"))
          }
      })
  }

  def updateUser: Action[AnyContent] = AdminAction.async { implicit request =>
    updateUserForm.bindFromRequest.fold(
      formWithErrors => {
        Logger.error(s"Received a bad request for user manage $formWithErrors")
        val errors = for(error <- formWithErrors.errors) yield error.message
        Future.successful(BadRequest(errors.mkString(" ")))
      },
      userInfo => {
        usersRepository.update(UpdatedUserInfo(userInfo.id, userInfo.active, userInfo.password))
          .flatMap { result =>
            if (result.ok) {
              Logger.info(s"User details successfully updated for ${userInfo.id}")
              Future.successful(Ok("User details successfully updated"))
            }
            else {
              Future.successful(InternalServerError("Something went wrong!"))
            }
          }
      })
  }

}
