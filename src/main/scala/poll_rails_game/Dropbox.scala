package poll_rails_game

import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2

object Dropbox {
  //TODO cannot upload this to git!
  private val ACCESS_TOKEN = "qfQdBVYoRAwAAAAAAAAsXAoIK2cCjKf9HBabZ2qIJADHsKAokbmJM4zouv5f86ql";

  def makeClient(): DbxClientV2 = {
    val config = DbxRequestConfig.newBuilder("dropbox/java-tutorial").build()
    new DbxClientV2(config, ACCESS_TOKEN)
  }

  def checkInfo(): Unit = {
    val client = makeClient()
    val account = client.users().getCurrentAccount()
    System.out.println(account.getName().getDisplayName())
  }
}