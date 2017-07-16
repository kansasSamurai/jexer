/*
 * Jexer - Java Text User Interface
 *
 * The MIT License (MIT)
 *
 * Copyright (C) 2017 Kevin Lamonte
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 *
 * @author Kevin Lamonte [kevin.lamonte@gmail.com]
 * @version 1
 */
package jexer;

import jexer.event.TKeypressEvent;
import jexer.event.TMouseEvent;
import static jexer.TKeypress.*;

/**
 * TTreeView implements a simple tree view.
 */
public class TTreeView extends TScrollableWidget {

    /**
     * Root of the tree.
     */
    private TTreeItem treeRoot;

    /**
     * Get the root of the tree.
     *
     * @return the root of the tree
     */
    public final TTreeItem getTreeRoot() {
        return treeRoot;
    }

    /**
     * Set the root of the tree.
     *
     * @param treeRoot the new root of the tree
     */
    public final void setTreeRoot(final TTreeItem treeRoot) {
        this.treeRoot = treeRoot;
    }

    /**
     * Maximum width of a single line.
     */
    private int maxLineWidth;

    /**
     * Only one of my children can be selected.
     */
    private TTreeItem selectedItem = null;

    /**
     * If true, move the window to put the selected item in view.  This
     * normally only happens once after setting treeRoot.
     */
    private boolean centerWindow = false;

    /**
     * The action to perform when the user selects an item.
     */
    private TAction action = null;

    /**
     * Set treeRoot.
     *
     * @param treeRoot ultimate root of tree
     * @param centerWindow if true, move the window to put the root in view
     */
    public void setTreeRoot(final TTreeItem treeRoot,
        final boolean centerWindow) {

        this.treeRoot = treeRoot;
        this.centerWindow = centerWindow;
    }

    /**
     * Public constructor.
     *
     * @param parent parent widget
     * @param x column relative to parent
     * @param y row relative to parent
     * @param width width of tree view
     * @param height height of tree view
     */
    public TTreeView(final TWidget parent, final int x, final int y,
        final int width, final int height) {

        this(parent, x, y, width, height, null);
    }

    /**
     * Public constructor.
     *
     * @param parent parent widget
     * @param x column relative to parent
     * @param y row relative to parent
     * @param width width of tree view
     * @param height height of tree view
     * @param action action to perform when an item is selected
     */
    public TTreeView(final TWidget parent, final int x, final int y,
        final int width, final int height, final TAction action) {

        super(parent, x, y, width, height);
        this.action = action;

        vScroller = new TVScroller(this, getWidth() - 1, 0, getHeight() - 1);
        hScroller = new THScroller(this, 0, getHeight() - 1, getWidth() - 1);
    }

    /**
     * Get the tree view item that was selected.
     *
     * @return the selected item, or null if no item is selected
     */
    public final TTreeItem getSelected() {
        return selectedItem;
    }

    /**
     * Set the new selected tree view item.
     *
     * @param item new item that became selected
     */
    public void setSelected(final TTreeItem item) {
        if (item != null) {
            item.setSelected(true);
        }
        if ((selectedItem != null) && (selectedItem != item)) {
            selectedItem.setSelected(false);
        }
        selectedItem = item;
    }

    /**
     * Perform user selection action.
     */
    public void dispatch() {
        if (action != null) {
            action.DO();
        }
    }

    /**
     * Resize text and scrollbars for a new width/height.
     */
    @Override
    public void reflowData() {
        int selectedRow = 0;
        boolean foundSelectedRow = false;

        if (treeRoot == null) {
            return;
        }

        // Make each child invisible/inactive to start, expandTree() will
        // reactivate the visible ones.
        for (TWidget widget: getChildren()) {
            if (widget instanceof TTreeItem) {
                TTreeItem item = (TTreeItem) widget;
                item.setInvisible(true);
                item.setEnabled(false);
                item.keyboardPrevious = null;
                item.keyboardNext = null;
            }
        }

        // Expand the tree into a linear list
        getChildren().clear();
        getChildren().addAll(treeRoot.expandTree("", true));

        // Locate the selected row and maximum line width
        for (TWidget widget: getChildren()) {
            TTreeItem item = (TTreeItem) widget;

            if (item == selectedItem) {
                foundSelectedRow = true;
            }
            if (!foundSelectedRow) {
                selectedRow++;
            }

            int lineWidth = item.getText().length()
                + item.getPrefix().length() + 4;
            if (lineWidth > maxLineWidth) {
                maxLineWidth = lineWidth;
            }
        }

        if ((centerWindow) && (foundSelectedRow)) {
            if ((selectedRow < getVerticalValue())
                || (selectedRow > getVerticalValue() + getHeight() - 2)
            ) {
                setVerticalValue(selectedRow);
                centerWindow = false;
            }
        }
        updatePositions();

        // Rescale the scroll bars
        setBottomValue(getChildren().size() - getHeight() + 1);
        if (getBottomValue() < 0) {
            setBottomValue(0);
        }
        if (getVerticalValue() > getBottomValue()) {
            setVerticalValue(getBottomValue());
        }
        setRightValue(maxLineWidth - getWidth() + 3);
        if (getRightValue() < 0) {
            setRightValue(0);
        }
        if (getHorizontalValue() > getRightValue()) {
            setHorizontalValue(getRightValue());
        }
        getChildren().add(hScroller);
        getChildren().add(vScroller);
    }

    /**
     * Update the Y positions of all the children items.
     */
    private void updatePositions() {
        if (treeRoot == null) {
            return;
        }

        int begin = getVerticalValue();
        int topY = 0;

        // As we walk the list we also adjust next/previous pointers,
        // resulting in a doubly-linked list but only of the expanded items.
        TTreeItem p = null;

        for (int i = 0; i < getChildren().size(); i++) {
            if (!(getChildren().get(i) instanceof TTreeItem)) {
                // Skip the scrollbars
                continue;
            }
            TTreeItem item = (TTreeItem) getChildren().get(i);

            if (p != null) {
                item.keyboardPrevious = p;
                p.keyboardNext = item;
            }
            p = item;

            if (i < begin) {
                // Render invisible
                item.setEnabled(false);
                item.setInvisible(true);
                continue;
            }

            if (topY >= getHeight() - 1) {
                // Render invisible
                item.setEnabled(false);
                item.setInvisible(true);
                continue;
            }

            item.setY(topY);
            item.setEnabled(true);
            item.setInvisible(false);
            item.setWidth(getWidth() - 1);
            topY++;
        }

    }

    /**
     * Handle mouse press events.
     *
     * @param mouse mouse button press event
     */
    @Override
    public void onMouseDown(final TMouseEvent mouse) {
        if (mouse.isMouseWheelUp()) {
            verticalDecrement();
        } else if (mouse.isMouseWheelDown()) {
            verticalIncrement();
        } else {
            // Pass to children
            super.onMouseDown(mouse);
        }

        // Update the screen after the scrollbars have moved
        reflowData();
    }

    /**
     * Handle mouse release events.
     *
     * @param mouse mouse button release event
     */
    @Override
    public void onMouseUp(final TMouseEvent mouse) {
        // Pass to children
        super.onMouseDown(mouse);

        // Update the screen after any thing has expanded/contracted
        reflowData();
    }

    /**
     * Handle keystrokes.
     *
     * @param keypress keystroke event
     */
    @Override
    public void onKeypress(final TKeypressEvent keypress) {
        if (keypress.equals(kbShiftLeft)
            || keypress.equals(kbCtrlLeft)
            || keypress.equals(kbAltLeft)
        ) {
            horizontalDecrement();
        } else if (keypress.equals(kbShiftRight)
            || keypress.equals(kbCtrlRight)
            || keypress.equals(kbAltRight)
        ) {
            horizontalIncrement();
        } else if (keypress.equals(kbShiftUp)
            || keypress.equals(kbCtrlUp)
            || keypress.equals(kbAltUp)
        ) {
            verticalDecrement();
        } else if (keypress.equals(kbShiftDown)
            || keypress.equals(kbCtrlDown)
            || keypress.equals(kbAltDown)
        ) {
            verticalIncrement();
        } else if (keypress.equals(kbShiftPgUp)
            || keypress.equals(kbCtrlPgUp)
            || keypress.equals(kbAltPgUp)
        ) {
            bigVerticalDecrement();
        } else if (keypress.equals(kbShiftPgDn)
            || keypress.equals(kbCtrlPgDn)
            || keypress.equals(kbAltPgDn)
        ) {
            bigVerticalIncrement();
        } else if (keypress.equals(kbHome)) {
            toTop();
        } else if (keypress.equals(kbEnd)) {
            toBottom();
        } else if (keypress.equals(kbEnter)) {
            if (selectedItem != null) {
                dispatch();
            }
        } else if (keypress.equals(kbUp)) {
            // Select the previous item
            if (selectedItem != null) {
                TTreeItem oldItem = selectedItem;
                if (selectedItem.keyboardPrevious != null) {
                    setSelected(selectedItem.keyboardPrevious);
                    if (oldItem.getY() == 0) {
                        verticalDecrement();
                    }
                }
            }
        } else if (keypress.equals(kbDown)) {
            // Select the next item
            if (selectedItem != null) {
                TTreeItem oldItem = selectedItem;
                if (selectedItem.keyboardNext != null) {
                    setSelected(selectedItem.keyboardNext);
                    if (oldItem.getY() == getHeight() - 2) {
                        verticalIncrement();
                    }
                }
            }
        } else if (keypress.equals(kbTab)) {
            getParent().switchWidget(true);
            return;
        } else if (keypress.equals(kbShiftTab)
                || keypress.equals(kbBackTab)) {
            getParent().switchWidget(false);
            return;
        } else if (selectedItem != null) {
            // Give the TTreeItem a chance to handle arrow keys
            selectedItem.onKeypress(keypress);
        } else {
            // Pass other keys (tab etc.) on to TWidget's handler.
            super.onKeypress(keypress);
            return;
        }

        // Update the screen after any thing has expanded/contracted
        reflowData();
    }

}
