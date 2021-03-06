/*
 * Copyright (c) 2016 Kaj Magnus Lindberg
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

/// <reference path="../utils/PatternInput.more.ts" />
/// <reference path="../util/stupid-dialog.more.ts" />
/// <reference path="../slim-bundle.d.ts" />

//------------------------------------------------------------------------------
   namespace debiki2.pagedialogs {
//------------------------------------------------------------------------------

const r = ReactDOMFactories;
const ModalHeader = rb.ModalHeader;
const ModalTitle = rb.ModalTitle;
const ModalBody = rb.ModalBody;
const ModalFooter = rb.ModalFooter;
const PatternInput = utils.PatternInput;

let movePostsDialog;


export function openMovePostsDialog(store: Store, post: Post, closeCaller, at: Rect) {
  if (!movePostsDialog) {
    movePostsDialog = ReactDOM.render(MovePostsDialog(), utils.makeMountNode());
  }
  movePostsDialog.open(store, post, closeCaller, at);
}


const MovePostsDialog = createComponent({
  getInitialState: function () {
    return {};
  },

  open: function(store: Store, post: Post, closeCaller, at) {
    this.setState({
      isOpen: true,
      store: store,
      post: post,
      newParentUrl: '',
      closeCaller: closeCaller,
      atRect: at,
      windowWidth: window.innerWidth,
    });
  },

  close: function() {
    this.setState({ isOpen: false, store: null, post: null });
  },

  doMove: function() {
    const store: Store = this.state.store;
    const post: Post = this.state.post;
    Server.movePost(post.uniqueId, this.state.newHost, this.state.newPageId,
        this.state.newParentNr, (postAfter: Post) => {
      if (store.currentPageId === this.state.newPageId) {
        // Within the same page, then scroll to the new location.
        // COULD add Back entry, which navigates back to the former parent or any
        // sibling just above.
        debiki.internal.showAndHighlightPost($byId('post-' + postAfter.nr));
      }
      else {
        // Let the user decide him/herself if s/he wants to open a new page.
        const newPostUrl = '/-' + this.state.newPageId + '#post-' + postAfter.nr;
        util.openDefaultStupidDialog({
          body: r.div({},
            "Moved. ", r.a({ href: newPostUrl }, "Click here to view it."))
        });
      }
      if (this.state.closeCaller) this.state.closeCaller();
      this.close();
    });
  },

  previewNewParent: function() {
    window.open(this.state.newParentUrl);
  },

  render: function () {
    const content = r.div({},
      // Skip i18n, this is for staff only, right?
      r.p({}, "Move post to where? Specify a new parent post, can be on a different page."),
      PatternInput({ type: 'text', label: "URL to new parent post:",
        help: r.span({}, "Tips: Click the ", r.span({ className: 'icon-link' }), " link " +
          "below the destination post, to copy its URL"),
        onChangeValueOk: (value, ok) => {
          const matches = value.match(/((https?:\/\/)?([^/]+))?\/-([a-zA-Z0-9_]+)#post-([0-9]+)$/);
          if (!matches) {
            this.setState({ ok: false });
            return;
          }
          this.setState({
            newParentUrl: value,
            newHost: matches[3],
            newPageId: matches[4],
            newParentNr: parseInt(matches[5]),
            ok: ok
          });
        },
        regex: /^((https?:\/\/)?[^/]+)?\/-[a-zA-Z0-9_]+#post-[0-9]+/,
        message: "Invalid new parent post link, should be like: " + location.hostname +
            "/-[page_id]/#post-[post_nr]" }),
      PrimaryButton({ onClick: this.doMove, disabled: !this.state.ok }, "Move"),
      Button({ onClick: this.previewNewParent }, "Preview"));

    return (
      utils.DropdownModal({ show: this.state.isOpen, onHide: this.close, showCloseButton: true,
          atRect: this.state.atRect, windowWidth: this.state.windowWidth },
        ModalHeader({}, ModalTitle({}, "Move post")),
        ModalBody({}, content),
        ModalFooter({}, Button({ onClick: this.close }, "Cancel"))));
  }
});


//------------------------------------------------------------------------------
   }
//------------------------------------------------------------------------------
// vim: fdm=marker et ts=2 sw=2 tw=0 fo=r list
