package poll_rails_game

import java.io.File

object GameWatchConf {
  val GAME_ROOT = "/poll_rails_game"

  //TODO move this configuration to a file that is itself watched, making updating very easy
  //TODO the value should probably be a case class
  val games = Map(
    "train game example" -> GameWatchConf("poll_rails_game_testing@googlegroups.com", "2020 Test Game Q")
  )
}
case class GameWatchConf(email: String, gameName: String) {
  //TODO does this need to be pulled out?
  //TODO is there a way to make this not rely on them donig stuff, and instead more data in data out?
  // This needs to run the hacked rails to save screenshots to the given directory
  // It also needs to send the email
  def processEvent(md: ChangeMetadata): Option[EmailContent] =
    md match {
      case fmd: FileMD =>
        val file = fmd.pathLower.getOrElse { throw new IllegalArgumentException(s"Shouldn't be possible for a watched event not to have a path: $md")}
        // Run the hacked rails
        //TODO this needs to give us the location of the files, as well as the information for the title (eg OR 3.2 - C&O Jco)
        //RailsBridge.run(file, screenshotDir)
        // Send email...for testing, can get email infra working first
        //TODO this from should probably be configurable! really need to get a better configuration
        //  and key management story
        Some(EmailContent("jcoveney+poll_rails_game@gmail.com", email, s"$gameName - $file", file, List()))
      case _ => None
    }
}

object Main {
  // If the file is on the game path, will return Some(file), otherwise nothing
  private def getGameName(path: String): Option[String] = {
    val file = new File(path)
    Option(file.getParent()).flatMap { parent =>
      if (parent.equals(GameWatchConf.GAME_ROOT)) Some(file.getName())
      else getGameName(parent)
    }
  }

  def main(args: Array[String]): Unit = {
    //TODO need to do something with the result if there is one!
    val errors = Email.verifyEnvironment() ++ RailsBridge.verifyEnvironment() ++ Dropbox.verifyEnvironment()
    errors.foreach { println(_) }
    if (errors.nonEmpty) System.exit(1)

    //TODO seems weird to pass dropbox an access token it itself has access to, but I think that is an
    //  artifact on the fact that I haven't decided how to best manage the enviornmental dependencies
    Dropbox.longpoll(GameWatchConf.GAME_ROOT) { changes =>
      changes.foreach { change =>
        change.pathLower.flatMap { getGameName(_) }.flatMap { GameWatchConf.games.get(_) } match {
          //TODO logging
          case Some(processor) =>
            println(s"WATCHED: $change\nPROCESSOR: $processor")
            processor.processEvent(change).foreach { email =>
              val response = email.makeRequest()
              println("Email sent")
              println(response.getStatusCode())
              println(response.getBody())
              println(response.getHeaders())
            }
          case None =>
            println(s"UNWATCHED: $change")
        }
      }
    }

    // First, for every game I'm watching, need to get the files that already exist (for now punt on this)
    // Filter out games I am not watching (eg no game -> email map)
    //TODO what's the best way to dynamically reload configuration like that? For the time being just require a recompile, shouldn't be often
    // For every game, need a database of
    // - emails I have already sent
    // - the latest move whose email has been sent
    //TODO need to do what happens if a mistake is made and they roll back time
    // TO BEGIN WITH: punt on the database. It will only send emails if it is running (to start).
    //   It will send an email for every new file created on its watch
    //   (though could perhaps flag if a new file is back in time or something)
    //TODO idea! though I don't know if it is overkill. It's own config (and any other commands) can be done through the filewatcher. Eg if it sees a new config file, it will load it. May be overkill though
  }
}
