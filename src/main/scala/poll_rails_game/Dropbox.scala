package poll_rails_game

import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2

object Dropbox {
  def makeClient(accessToken: String): DbxClientV2 = {
    val config = DbxRequestConfig.newBuilder("dropbox/java-tutorial").build()
    new DbxClientV2(config, accessToken)
  }

  //TODO could use a reader...
  def checkInfo(accessToken: String): Unit = {
    val client = makeClient(accessToken)
    val account = client.users().getCurrentAccount()
    System.out.println(account.getName().getDisplayName())
  }
}