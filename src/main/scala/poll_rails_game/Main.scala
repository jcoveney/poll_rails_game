package poll_rails_game

import java.io.File
import java.nio.file.Files

import scala.io.Source

object GameWatchConf {
  val GAME_ROOT = "/poll_rails_game"
  val CONF_FILE = new File(GAME_ROOT, "conf.json").getAbsolutePath()

  // Leaves space to change to a real class later
  type Conf = Map[String, GameWatchConf]

  //TODO can change things so that if this file is updated it will download the updated version!
  def getConfFromDropbox(rev: Option[String]): Conf = {
    val localFile = Dropbox.downloadFile(CONF_FILE, rev)
    ujson.read(os.read(os.Path(localFile))).obj.view.mapValues { v => GameWatchConf(v("email").str, v("gameName").str) }.toMap
  }
}
case class GameWatchConf(email: String, gameName: String) {
  private val railsRegex = raw"[^.].*\.rails".r
  private def isValidRailsFile(file: String): Boolean =
    railsRegex matches new File(file).getName()

  //TODO does this need to be pulled out?
  //TODO is there a way to make this not rely on them donig stuff, and instead more data in data out?
  // This needs to run the hacked rails to save screenshots to the given directory
  // It also needs to send the email
  def processEvent(md: ChangeMetadata): Option[EmailContent] =
    md match {
      case fmd: FileMD if fmd.pathLower.fold(false){ isValidRailsFile(_) } =>
        //TODO if somehow pathLower isn't set, we need an alert, because that is weird. In general, need alerting infrastructure
        //  for serious issues
        fmd.pathLower.flatMap { dropboxGameFile =>
          //TODO have to use the dropbox API to get the file and save it somewhere local, b/c it's just in dropbox atm
          val tmpDir = Files.createTempDirectory("poll_rails_game").toFile().getAbsolutePath()
          println(s"Temp directory for rails files: $tmpDir")
          val localGameFile = Dropbox.saveFileToTmp(dropboxGameFile, fmd.rev, Some(tmpDir))
          //TODO make sure that this is robust to the case where the rails bridge fails, especially as we seek to make things more robust
          if (RailsBridge.run(localGameFile, tmpDir)) {
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
            // also, certain actions generate more than one line! So I think I need to handle that on the rails side
            val body = (List(dropboxGameFile, "", "==== actions are descending (as in rails) ====") ++ actions.takeRight(16) ++ List("========== most recent action ===========")).mkString("\n")
            Some(EmailContent("jcoveney+poll_rails_game@gmail.com", email, s"$gameName - $roundInfo", body, outputMap))
          } else {
            print("Detected error when running rails. Investigate!")
            None
          }
        }
      case _ =>
        println("Only watching FileMetadata events on valid rails files. Ignoring")
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
    val initialGameConf = GameWatchConf.getConfFromDropbox(None)
    println("Initial configuration loaded")
    println(initialGameConf)

    //TODO if we do want to support uploading a new conf.json, then we need the longpoll function to support
    //  passing state through...state monad?
    Dropbox.longpoll(GameWatchConf.GAME_ROOT, initialGameConf) { (gameConf, changes) =>
      changes.foldLeft(gameConf) { (curGameConf, change) =>
        change match {
          case confChange: FileMD if confChange.pathLower.fold(false) { _ == GameWatchConf.CONF_FILE } =>
            println("Change to configuration detected. Loading new configuration")
            // We do the rev b/c if the conf suddenly changes a bunch, we may have a bunch of changes
            // to process and I would rather process them in order rather than pull the most recent which
            // which could get confusing! Maybe that doesn't matter though!
            val newGameConf = GameWatchConf.getConfFromDropbox(Some(confChange.rev))
            println("New configuration loaded")
            println(newGameConf)
            newGameConf
          case otherChange =>
            otherChange.pathLower.flatMap { getGameName(_) }.flatMap { curGameConf.get(_) } match {
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
            curGameConf
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
