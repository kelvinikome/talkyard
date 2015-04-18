/**
 * Copyright (C) 2015 Kaj Magnus Lindberg (born 1979)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package debiki.dao.migrations

import com.debiki.core._
import com.debiki.core.Prelude._
import java.{util => ju}

import scala.collection.mutable


object Migration14 {

  var siteTransaction: SiteTransaction = null

  def run(systemTransaction: SystemTransaction) {
    val allSites = systemTransaction.loadSites()
    for (site <- allSites) {
      siteTransaction = systemTransaction.siteTransaction(site.id)
      val allPageMetas = siteTransaction.loadAllPageMetas()
      for (pageMeta <- allPageMetas) {
        migratePage(pageMeta)
      }
    }


    def migratePage(pageMeta: PageMeta) {
      val partsOld: PageParts =
        siteTransaction.loadPagePartsOld(pageMeta.pageId) getOrDie "DwE0Pk3W2"

      // Migrate posts.
      for (oldPost <- partsOld.getAllPosts) {
        val newPost = upgradePost(pageMeta.pageId, oldPost)
        siteTransaction.insertPost(newPost)
      }

      // Migrate Like and Wrong votes. Ditch off-topic votes — replace them with Rude and Boring?
      val alreadyVoted = mutable.HashSet[String]()
      val postIdsWithVotes = mutable.HashSet[PostId]()
      for (postActionOld <- partsOld.getAllActions) postActionOld.payload match {
        case vote: PostActionPayload.Vote if vote != PostActionPayload.VoteOffTopic =>
          val voteType = vote match {
            case PostActionPayload.VoteLike => PostVoteType.Like
            case PostActionPayload.VoteWrong => PostVoteType.Wrong
            case PostActionPayload.VoteOffTopic => die("DwE0gKFF3")
          }
          val voterId = postActionOld.userId.toInt
          val postId = postActionOld.postId
          val voteKey = s"$postId:$voteType:$voterId"
          if (!alreadyVoted.contains(voteKey)) {
            alreadyVoted.add(voteKey)
            postIdsWithVotes.add(postId)
            siteTransaction.insertVote(pageMeta.pageId, postId, voteType, voterId = voterId)
          }
        case _ =>
          // skip
      }

      // Don't migrate flags and pinned positions.

      // Update vote counts
      for (postId <- postIdsWithVotes) {
        val postNoCounts = siteTransaction.loadThePost(pageMeta.pageId, postId)
        val actions = siteTransaction.loadActionsDoneToPost(pageMeta.pageId, postId)
        val readStats = siteTransaction.loadPostsReadStats(pageMeta.pageId, Some(postId))
        val postWithCounts = postNoCounts.copyWithUpdatedVoteAndReadCounts(actions, readStats)
        siteTransaction.updatePost(postWithCounts)
      }
    }


    def upgradePost(pageId: PageId, oldPost: Post): Post2 = {
      val collapsedStatus =
        if (oldPost.isTreeCollapsed) Some(CollapsedStatus.TreeCollapsed)
        else if (oldPost.isPostCollapsed) Some(CollapsedStatus.PostCollapsed)
        else None

      val closedStatus =
        if (oldPost.isTreeClosed) Some(ClosedStatus.TreeClosed)
        else None

      val deletedStatus =
        if (oldPost.isTreeDeleted) Some(DeletedStatus.TreeDeleted)
        else if (oldPost.isPostDeleted) Some(DeletedStatus.PostDeleted)
        else None

      var safeVersion =
        if (oldPost.lastApprovalDati.isDefined) Some(Post2.FirstVersion) else None

      // Add some dummy text to empty posts. Empty posts are no longer allowed.
      var approvedSource = oldPost.approvedText
      var approvedHtmlSanitized = oldPost.approvedHtmlSanitized
      var approvedById = oldPost.lastManuallyApprovedById.map(_.toInt) orElse
        oldPost.lastApprovalDati.map(_ => SystemUser.User.id2)
      var approvedAt = oldPost.lastApprovalDati
      val isEmpty =
        approvedSource.map(_.trim.length > 0) != Some(true) ||
          approvedHtmlSanitized.map(_.trim.length > 0) != Some(true)
      if (isEmpty) {
        safeVersion = Some(Post2.FirstVersion)
        approvedSource = Some("(empty post)")
        approvedHtmlSanitized = Some("<p>(empty post)</p>")
        approvedById = Some(SystemUser.User.id2)
        approvedAt = Some(oldPost.creationDati)
      }

      Post2(
        siteId = siteTransaction.siteId,
        pageId = pageId,
        id = oldPost.id,
        parentId = oldPost.parentId,
        multireplyPostIds = oldPost.multireplyPostIds,
        createdAt = oldPost.creationDati,
        createdById = oldPost.userId.toInt,
        // Let's delete all edit history.
        lastEditedAt = None,
        lastEditedById = None,
        lastApprovedEditAt = None,
        lastApprovedEditById = None,
        numDistinctEditors = 0,
        safeVersion = safeVersion,
        approvedSource = approvedSource,
        approvedHtmlSanitized = approvedHtmlSanitized,
        approvedAt = approvedAt,
        approvedById = approvedById,
        approvedVersion = safeVersion,
        currentSourcePatch = None, // ignore unapproved edits
        currentVersion = Post2.FirstVersion,
        collapsedStatus = collapsedStatus,
        collapsedAt = collapsedStatus.map(_ => oldPost.creationDati),
        collapsedById = collapsedStatus.map(_ => SystemUser.User.id2),
        closedStatus = closedStatus,
        closedAt = closedStatus.map(_ => oldPost.creationDati),
        closedById = closedStatus.map(_ => SystemUser.User.id2),
        hiddenAt = None,
        hiddenById = None,
        deletedStatus = deletedStatus,
        deletedAt = deletedStatus.map(_ => oldPost.creationDati),
        deletedById = deletedStatus.map(_ => SystemUser.User.id2),
        pinnedPosition = None,
        numPendingFlags = 0,
        numHandledFlags = 0,
        numPendingEditSuggestions = 0,
        numLikeVotes = 0,
        numWrongVotes = 0,
        numTimesRead = 0)
    }
  }
}