package poll_rails_game

import scala.collection.mutable.Buffer

object RailsBridge {
  val RAILS_JAR_LOCATION = System.getenv("RAILS_JAR_LOCATION")
  val PATH_TO_FX = System.getenv("PATH_TO_FX")

  //TODO should probably verify that it actually exists
  def verifyEnvironment(): List[String] =
    (if (Option(RAILS_JAR_LOCATION).isEmpty) List("Must set RAILS_JAR_LOCATION") else List()) ++
    (if (Option(PATH_TO_FX).isEmpty) List("Must set PATH_TO_FX") else List())

  //TODO should return an error code etc
  //TODO probably the most important thing at this point is ensure that if this fails, things are handled
  //  gracefully. gracefully here likely means:
  //    - easily recovered logs
  //    - I am alerted
  //    - the whole app does not crash (this also involved downstream pieces)
  def run(gameSave: String, screenshotDir: String): Boolean = {
    // Some mutability is inevitable with the design of the os package
    val stdoutLines = Buffer[String]()
    var stderrLines = Buffer[String]()

    //TODO need to figure out how to make stdout and strerr such that they log to shell, but can also
    //  be in the body of the email. Can I have two pipes? Or will I have to implement one myself?
    val result = os.proc(
      "java",
      "--module-path", PATH_TO_FX, "--add-modules", "javafx.controls,javafx.swing",
      "-cp", RAILS_JAR_LOCATION,
      "net.sf.rails.util.PrintGame", screenshotDir, gameSave
    ).call(
      stdout = os.ProcessOutput.Readlines { line =>
        println(s"stdout: $line")
        stdoutLines.append(line)
      },
      stderr = os.ProcessOutput.Readlines { line =>
        println(s"stderr: $line")
        stderrLines.append(line)
      } ,
      check = false)
    //TODO should we consider doing more here around the state of things?
    //  at the very least, should probably do some logging
    val successful = result.exitCode == 0
    if (!successful) {
      //TODO oddly, this email seems to consistently fail to send?
      val delimiter = "=================="
      val body = List(s"Game save: $gameSave", "", "stdout", delimiter) ++ stdoutLines ++ List(delimiter, "", "stderr", delimiter) ++ stderrLines ++ List(delimiter)
      EmailContent("jcoveney+poll_rails_game@gmail.com", "jcoveney@gmail.com", "Errors detected", body.mkString("\n"), Map()).makeRequest()
    }
    successful
  }
}

//TODO seems more ideal to break positionTitle down into OR, round, player, railroad (if applicable)
case class GameInformation(positionTitle: String, images: List[String])