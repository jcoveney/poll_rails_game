package poll_rails_game

import java.io.File
import java.nio.file.Files

import scala.io.Source

object GameWatchConf {
  val GAME_ROOT = "/poll_rails_game"

  //TODO can change things so that if this file is updated it will download the updated version!
  def getConfFromDropbox(): Map[String, GameWatchConf] = {
    val localFile = Dropbox.downloadFile(new File(GAME_ROOT, "conf.json").getAbsolutePath())
    ujson.read(os.read(os.Path(localFile))).obj.mapValues { v => GameWatchConf(v("email").str, v("gameName").str) }.toMap
  }
}
case class GameWatchConf(email: String, gameName: String) {
  //TODO does this need to be pulled out?
  //TODO is there a way to make this not rely on them donig stuff, and instead more data in data out?
  // This needs to run the hacked rails to save screenshots to the given directory
  // It also needs to send the email
  def processEvent(md: ChangeMetadata): Option[EmailContent] =
    md match {
      case fmd: FileMD =>
        val dropboxGameFile = fmd.pathLower.getOrElse { throw new IllegalArgumentException(s"Shouldn't be possible for a watched event not to have a path: $md")}
        //TODO have to use the dropbox API to get the file and save it somewhere local, b/c it's just in dropbox atm
        val tmpDir = Files.createTempDirectory("poll_rails_game").toFile().getAbsolutePath()
        println(s"Temp directory for rails files: $tmpDir")
        val localGameFile = Dropbox.saveFileToTmp(dropboxGameFile, fmd.rev, Some(tmpDir))
        RailsBridge.run(localGameFile, tmpDir)
        val outputMap = List("status_window.png", "or_window.png", "stock_market.png", "game_report.txt").map { n => (n -> new File(tmpDir, n).getAbsolutePath()) }.toMap
        // Run the hacked rails
        //TODO this needs to give us the location of the files, as well as the information for the title (eg OR 3.2 - C&O Jco)
        //RailsBridge.run(file, screenshotDir)
        // Send email...for testing, can get email infra working first
        //TODO this from should probably be configurable! really need to get a better configuration
        //  and key management story
        val roundInfo = Source.fromFile(new File(tmpDir, "round_facade.txt")).getLines().next()
        val actions = Source.fromFile(new File(tmpDir, "game_report.txt")).getLines().toList
        //TODO how many actions? I think ideal would be "number of players*2", but then we need to know number of players
        val body = (List(dropboxGameFile, "", "most recent", "=================") ++ actions.reverse.take(8)).mkString("\n")
        Some(EmailContent("jcoveney+poll_rails_game@gmail.com", email, s"$gameName - $roundInfo", body, outputMap))
      case _ =>
        println("Only watching FileMetadata events. Ignoring")
        None
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

    //TODO should print this out
    val games = GameWatchConf.getConfFromDropbox()

    //TODO if we do want to support uploading a new conf.json, then we need the longpoll function to support
    //  passing state through...state monad?
    Dropbox.longpoll(GameWatchConf.GAME_ROOT) { changes =>
      changes.foreach { change =>
        change.pathLower.flatMap { getGameName(_) }.flatMap { games.get(_) } match {
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
