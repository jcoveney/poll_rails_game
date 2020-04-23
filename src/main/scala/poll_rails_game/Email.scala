package poll_rails_game

import java.io.File
import java.io.FileInputStream

import com.sendgrid.Method
import com.sendgrid.Request
import com.sendgrid.Response
import com.sendgrid.SendGrid
import com.sendgrid.helpers.mail.Mail
import com.sendgrid.helpers.mail.objects.Attachments
import com.sendgrid.helpers.mail.objects.Content
import com.sendgrid.helpers.mail.objects.{Email => SGEmail}

object Email {
  //TODO the fact that these various objects all do this is telling. I wonder if a macro woudl apply here...
  //  if it really bothers me, should probably check what configuration libraries are floating around
  //TODO really need to think about how I want to deal with these credentials. I hate just having them
  //  be these vals...and yet...
  val SENDGRID_API_KEY = System.getenv("SENDGRID_API_KEY")

  def verifyEnvironment(): List[String] =
    if (Option(SENDGRID_API_KEY).isEmpty) List("Must set SENDGRID_API_KEY") else List()
}

case class EmailContent(
  from_address: String,
  to_address: String,
  title: String,
  body: String,
  images: Map[String, String]
) {
  def makeRequest(): Response = {
    val mail = new Mail(new SGEmail(from_address), title, new SGEmail(to_address), new Content("text/plain", body))

    images.foreach {
      case (title, image) =>
        mail.addAttachments(
          new Attachments.Builder(new File(image).getName(), new FileInputStream(image))
            .withType("image/png")
            .withDisposition("inline")
            //TODO couldn't find an example of what the content id should be
            .withContentId(title)
            .build()
        )
    }

    val request = new Request()
    request.setMethod(Method.POST)
    request.setEndpoint("mail/send")
    request.setBody(mail.build())
    //TODO can we print the object that we are sending?
    println(s"Attempting to send email generated from: $this")
    val response = new SendGrid(Email.SENDGRID_API_KEY).api(request)
    //TODO it is kind of gross to do this here and yet...don't see the need to make it prettier yet.
    //  if I use it more, definitely should clean up logging
    println("Email sent")
    println(response.getStatusCode())
    println(response.getBody())
    println(response.getHeaders())
    response
  }
}
