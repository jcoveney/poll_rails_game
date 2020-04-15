package poll_rails_game

import scala.jdk.CollectionConverters._
import annotation.tailrec

import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.http.StandardHttpRequestor
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.DeletedMetadata
import com.dropbox.core.v2.files.ExportInfo
import com.dropbox.core.v2.files.FileLockMetadata
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.FolderMetadata
import com.dropbox.core.v2.files.FolderSharingInfo
import com.dropbox.core.v2.files.FileSharingInfo
import com.dropbox.core.v2.files.ListFolderResult
import com.dropbox.core.v2.files.MediaInfo
import com.dropbox.core.v2.files.SymlinkInfo
import com.dropbox.core.v2.fileproperties.PropertyGroup

import java.util.Date
import java.util.concurrent.TimeUnit

object ChangeMetadata {
  def apply(lfr: ListFolderResult): List[ChangeMetadata] =
    lfr.getEntries().asScala.map { metadata =>
      if (metadata.isInstanceOf[DeletedMetadata]) {
        val md = metadata.asInstanceOf[DeletedMetadata]
        DeletedMD(
          md.getName(),
          Option(md.getPathLower()),
          Option(md.getPathDisplay()),
          Option(md.getParentSharedFolderId)
        )
      } else if (metadata.isInstanceOf[FileMetadata]) {
        val md = metadata.asInstanceOf[FileMetadata]
        FileMD(
          md.getName(),
          md.getId(),
          md.getClientModified(),
          md.getServerModified(),
          md.getRev(),
          md.getSize(),
          Option(md.getPathLower()),
          Option(md.getPathDisplay()),
          Option(md.getParentSharedFolderId()),
          Option(md.getMediaInfo()),
          Option(md.getSymlinkInfo()),
          Option(md.getSharingInfo()),
          md.getIsDownloadable(),
          Option(md.getExportInfo()),
          Option(md.getPropertyGroups()).map{_.asScala.toList}.getOrElse(List()),
          Option(md.getHasExplicitSharedMembers()),
          Option(md.getContentHash()),
          Option(md.getFileLockInfo)
        )
      } else if (metadata.isInstanceOf[FolderMetadata]) {
        val md = metadata.asInstanceOf[FolderMetadata]
        FolderMD(
          md.getName(),
          md.getId(),
          Option(md.getPathLower()),
          Option(md.getPathDisplay()),
          Option(md.getParentSharedFolderId()),
          Option(md.getSharedFolderId()),
          Option(md.getSharingInfo()),
          Option(md.getPropertyGroups()).map{_.asScala.toList}.getOrElse(List())
        )
      } else throw new IllegalArgumentException(metadata.toString)
    }.toList
}
sealed trait ChangeMetadata
case class DeletedMD(
  name: String,
  pathLower: Option[String],
  pathDisplay: Option[String],
  parentSharedFolderId: Option[String]
) extends ChangeMetadata
case class FileMD(
  name: String,
  id: String,
  clientModified: Date,
  serverModified: Date,
  rev: String,
  size: Long,
  pathLower: Option[String],
  pathDisplay: Option[String],
  parentSharedFolderId: Option[String],
  //TODO do I need to do the fancy treatment here?
  mediaInfo: Option[MediaInfo],
  //TODO do I need to do the fancy treatment here?
  symlinkInfo: Option[SymlinkInfo],
  //TODO do I need to do the fancy treatment here?
  sharingInfo: Option[FileSharingInfo],
  isDownloadable: Boolean,
  //TODO do I need to do the fancy treatment here?
  exportInfo: Option[ExportInfo],
  //TODO do I need to do the fancy treatment here?
  propertyGroups: List[PropertyGroup],
  hasExplicitSharedMembers: Option[Boolean],
  contentHash: Option[String],
  //TODO do I need to do the fancy treatment here?
  fileLockInfo: Option[FileLockMetadata]
) extends ChangeMetadata
case class FolderMD(
  name: String,
  id: String,
  pathLower: Option[String],
  pathDisplay: Option[String],
  parentSharedFolderId: Option[String],
  sharedFolderId: Option[String],
  //TODO do I need to do the fancy treatment here?
  sharingInfo: Option[FolderSharingInfo],
  //TODO do I need to do the fancy treatment here?
  propertyGroups: List[PropertyGroup]
) extends ChangeMetadata
//TODO could use a reader for all of the accessToken malarkey
object Dropbox {
  private val STANDARD_CONFIG = StandardHttpRequestor.Config.DEFAULT_INSTANCE
  private val LONGPOLL_CONFIG = STANDARD_CONFIG.copy().withReadTimeout(5, TimeUnit.MINUTES).build()
  private val LONGPOLL_TIMEOUT_SECS = TimeUnit.MINUTES.toSeconds(2)

  def makeClient(config: StandardHttpRequestor.Config, accessToken: String): DbxClientV2 = {
    val clientUserAgentId = "poll_rails_game"
    val requestor = new StandardHttpRequestor(config)
    val requestConfig = DbxRequestConfig.newBuilder(clientUserAgentId).withHttpRequestor(requestor).build()
    return new DbxClientV2(requestConfig, accessToken);
  }

  //TODO consider converting ListFolderResult into something nicer
  def longpoll(path: String, accessToken: String)(fn: List[ChangeMetadata] => Unit): Unit = {
    val standardClient = makeClient(STANDARD_CONFIG, accessToken)
    val longpollClient = makeClient(LONGPOLL_CONFIG, accessToken)

    val standardCursor = getLatestCursor(standardClient, path)
    //TODO logging
    println("Longpolling for changes... press CTRL-C to exit.")
    @tailrec
    def go(goCursor: String): Unit = {
      val longpollResult = longpollClient.files().listFolderLongpoll(goCursor, LONGPOLL_TIMEOUT_SECS)
      val newGoCursor = if (longpollResult.getChanges()) {
        @tailrec
        def processChanges(processChangesCursor: String): String = {
          val standardResult = standardClient.files().listFolderContinue(processChangesCursor)
          fn(ChangeMetadata(standardResult))
          val newProcessChangesCursor = standardResult.getCursor()
          if (standardResult.getHasMore()) processChanges(newProcessChangesCursor) else newProcessChangesCursor
        }
        processChanges(goCursor)
      } else goCursor
      Option(longpollResult.getBackoff()).foreach { backoff =>
        println(s"backing off for ${backoff}s")
        Thread.sleep(TimeUnit.SECONDS.toMillis(backoff))
      }
      go(newGoCursor)
    }
    go(standardCursor)
  }

  private def getLatestCursor(dbxClient: DbxClientV2, path: String): String =
    dbxClient.files()
      .listFolderGetLatestCursorBuilder(path)
      .withIncludeDeleted(true)
      .withIncludeMediaInfo(false)
      .withRecursive(true)
      .start()
      .getCursor()

  // This is just as a quick way to check that everything is working
  def checkInfo(accessToken: String): Unit = {
    val client = makeClient(STANDARD_CONFIG, accessToken)
    val account = client.users().getCurrentAccount()
    System.out.println(account.getName().getDisplayName())
  }
}