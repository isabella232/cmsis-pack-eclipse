/*******************************************************************************
 * Copyright (c) 2021 ARM Ltd. and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * ARM Ltd and ARM Germany GmbH - Initial API and implementation
 *******************************************************************************/

package com.arm.cmsis.pack.installer.ui.views;

import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;

import com.arm.cmsis.pack.CpPlugIn;
import com.arm.cmsis.pack.ICpPackManager;
import com.arm.cmsis.pack.common.CmsisConstants;
import com.arm.cmsis.pack.enums.EDeviceHierarchyLevel;
import com.arm.cmsis.pack.info.CpDeviceInfo;
import com.arm.cmsis.pack.info.ICpDeviceInfo;
import com.arm.cmsis.pack.installer.ui.IHelpContextIds;
import com.arm.cmsis.pack.installer.ui.Messages;
import com.arm.cmsis.pack.item.CmsisMapItem;
import com.arm.cmsis.pack.item.ICmsisMapItem;
import com.arm.cmsis.pack.rte.devices.IRteDeviceItem;
import com.arm.cmsis.pack.ui.CpPlugInUI;
import com.arm.cmsis.pack.ui.tree.AdvisedCellLabelProvider;
import com.arm.cmsis.pack.ui.tree.ColumnAdvisor;
import com.arm.cmsis.pack.ui.tree.TreeColumnComparator;
import com.arm.cmsis.pack.ui.tree.TreeObjectContentProvider;

/**
 * Default implementation of the devices view in pack manager
 */
public class DevicesView extends PackInstallerView {

    public static final String ID = "com.arm.cmsis.pack.installer.ui.views.DevicesView"; //$NON-NLS-1$

    static IRteDeviceItem getDeviceTreeItem(Object obj) {
        if (obj instanceof IRteDeviceItem) {
            return (IRteDeviceItem) obj;
        }
        return null;
    }

    static boolean stopAtCurrentLevel(IRteDeviceItem rteDeviceItem) {
        IRteDeviceItem firstChild = rteDeviceItem.getFirstChild();
        if (firstChild == null || firstChild.getLevel() == EDeviceHierarchyLevel.PROCESSOR.ordinal()) {
            return true;
        }
        return false;
    }

    static class DeviceViewContentProvider extends TreeObjectContentProvider {

        @Override
        public Object getParent(Object child) {
            IRteDeviceItem rteDeviceItem = getDeviceTreeItem(child);
            if (rteDeviceItem != null) {
                return rteDeviceItem.getParent();
            }
            return null;
        }

        @Override
        public Object[] getChildren(Object parent) {
            IRteDeviceItem rteDeviceItem = getDeviceTreeItem(parent);
            if (rteDeviceItem != null) {
                if (!rteDeviceItem.hasChildren() || stopAtCurrentLevel(rteDeviceItem)) {
                    return null;
                }
                return rteDeviceItem.getChildArray();
            }
            return super.getChildren(parent);
        }

        @Override
        public boolean hasChildren(Object parent) {
            IRteDeviceItem rteDeviceItem = getDeviceTreeItem(parent);
            if (rteDeviceItem != null) {
                if (stopAtCurrentLevel(rteDeviceItem)) {
                    return false;
                }
                return rteDeviceItem.hasChildren();
            }
            return super.hasChildren(parent);
        }
    }

    static class DevicesViewLabelProvider extends ColumnLabelProvider {

        @Override
        public String getText(Object obj) {
            IRteDeviceItem rteDeviceItem = getDeviceTreeItem(obj);
            if (rteDeviceItem != null) {
                // added spaces at last of text as a workaround to show the complete text in the
                // views
                String name = removeColon(rteDeviceItem.getName()) + ' ';
                if (!rteDeviceItem.hasChildren() && rteDeviceItem.getDevice() != null
                        && rteDeviceItem.getDevice().isDeprecated()) {
                    name += Messages.DevicesView_DeprecatedDevice + ' ';
                }
                return name;
            }
            return CmsisConstants.EMPTY_STRING;
        }

        private String removeColon(String string) {
            if (string.indexOf(':') != -1) {
                return string.substring(0, string.indexOf(':'));
            }
            return string;
        }

        @Override
        public Image getImage(Object obj) {
            IRteDeviceItem rteDeviceItem = getDeviceTreeItem(obj);
            if (rteDeviceItem != null) {
                if (rteDeviceItem.getLevel() == EDeviceHierarchyLevel.VENDOR.ordinal()) {
                    return CpPlugInUI.getImage(CpPlugInUI.ICON_COMPONENT);
                } else if (rteDeviceItem.hasChildren() && !stopAtCurrentLevel(rteDeviceItem)) {
                    return CpPlugInUI.getImage(CpPlugInUI.ICON_COMPONENT_CLASS);
                } else if (rteDeviceItem.getDevice() != null && rteDeviceItem.getDevice().isDeprecated()) {
                    return CpPlugInUI.getImage(CpPlugInUI.ICON_DEVICE_DEPR);
                } else if (packInstalledAndContainsDevice(rteDeviceItem)) {
                    return CpPlugInUI.getImage(CpPlugInUI.ICON_DEVICE);
                } else {
                    return CpPlugInUI.getImage(CpPlugInUI.ICON_DEVICE_GREY);
                }
            }

            return null;
        }

        private boolean packInstalledAndContainsDevice(IRteDeviceItem rteDeviceItem) {
            IRteDeviceItem deviceItem;
            if (rteDeviceItem.getDevice() == null) {
                IRteDeviceItem parent = getClosestParentRteDeviceItem(rteDeviceItem);
                deviceItem = parent.findItem(rteDeviceItem.getName(), rteDeviceItem.getVendorName(), false);
                if (deviceItem == null) {
                    return false;
                }
            } else {
                deviceItem = rteDeviceItem;
            }
            return deviceItem.getDevice().getPack().getPackState().isInstalledOrLocal();
        }

        @Override
        public String getToolTipText(Object obj) {
            IRteDeviceItem item = getDeviceTreeItem(obj);
            IRteDeviceItem parent = getClosestParentRteDeviceItem(item);
            IRteDeviceItem rteDeviceItem;
            if (parent == item) {
                rteDeviceItem = item;
            } else {
                rteDeviceItem = parent.findItem(item.getName(), item.getVendorName(), false);
            }
            if (rteDeviceItem != null && rteDeviceItem.getDevice() != null) {
                return NLS.bind(Messages.DevicesView_AvailableInPack, rteDeviceItem.getDevice().getPackId());
            }
            return null;
        }

        private IRteDeviceItem getClosestParentRteDeviceItem(IRteDeviceItem item) {
            IRteDeviceItem parent = item;
            while (parent.getParent() != null && parent.getParent().getAllDeviceNames().isEmpty()) {
                parent = parent.getParent();
            }
            return parent;
        }
    }

    static class DevicesViewColumnAdvisor extends ColumnAdvisor {

        public DevicesViewColumnAdvisor(ColumnViewer columnViewer) {
            super(columnViewer);
        }

        @Override
        public CellControlType getCellControlType(Object obj, int columnIndex) {
            if (columnIndex == COLURL) {
                IRteDeviceItem item = getDeviceTreeItem(obj);
                if (item != null) {
                    if (item.getLevel() == EDeviceHierarchyLevel.VARIANT.ordinal()
                            || (item.getLevel() == EDeviceHierarchyLevel.DEVICE.ordinal()
                                    && stopAtCurrentLevel(item))) {
                        return CellControlType.URL;
                    }
                }
            }
            return CellControlType.TEXT;
        }

        @Override
        public String getString(Object obj, int columnIndex) {
            if (getCellControlType(obj, columnIndex) == CellControlType.URL) {
                IRteDeviceItem item = getDeviceTreeItem(obj);
                if (item != null) {
                    ICpDeviceInfo deviceInfo = new CpDeviceInfo(null, item.getDevice(), item.getName());
                    return deviceInfo.getSummary();
                }
            } else if (columnIndex == COLURL) {
                IRteDeviceItem item = getDeviceTreeItem(obj);
                int nrofDevices = item.getAllDeviceNames().size();
                if (nrofDevices == 1) {
                    return Messages.DevicesView_1Device;
                } else if (nrofDevices == 0) {
                    return Messages.DevicesView_Processor;
                } else {
                    return nrofDevices + Messages.DevicesView_Devices;
                }
            }
            return null;
        }

        @Override
        public String getUrl(Object obj, int columnIndex) {
            if (getCellControlType(obj, columnIndex) == CellControlType.URL) {
                IRteDeviceItem item = getDeviceTreeItem(obj);
                return item.getUrl();
            }
            return null;
        }

        @Override
        public String getTooltipText(Object obj, int columnIndex) {
            return getUrl(obj, columnIndex);
        }
    }

    public DevicesView() {
    }

    @Override
    public boolean isFilterSource() {
        return true;
    }

    @Override
    protected String getHelpContextId() {
        return IHelpContextIds.DEVICES_VIEW;
    }

    @Override
    public void createTreeColumns() {
        fTree.setInitialText(Messages.DevicesView_SearchDevice);

        TreeViewerColumn column0 = new TreeViewerColumn(fViewer, SWT.LEFT);
        column0.getColumn().setText(CmsisConstants.DEVICE_TITLE);
        column0.getColumn().setWidth(200);
        column0.setLabelProvider(new DevicesViewLabelProvider());

        TreeViewerColumn column1 = new TreeViewerColumn(fViewer, SWT.LEFT);
        column1.getColumn().setText(CmsisConstants.SUMMARY_TITLE);
        column1.getColumn().setWidth(300);
        DevicesViewColumnAdvisor columnAdvisor = new DevicesViewColumnAdvisor(fViewer);
        column1.setLabelProvider(new AdvisedCellLabelProvider(columnAdvisor, COLURL));

        fViewer.setContentProvider(new DeviceViewContentProvider());
        fViewer.setComparator(new TreeColumnComparator(fViewer, columnAdvisor, 0));
        fViewer.setAutoExpandLevel(2);
    }

    @Override
    protected void refresh() {
        if (CpPlugIn.getDefault() == null) {
            return;
        }
        ICpPackManager packManager = CpPlugIn.getPackManager();
        if (packManager != null) {
            ICmsisMapItem<IRteDeviceItem> root = new CmsisMapItem<>();
            IRteDeviceItem allDevices = packManager.getDevices();
            root.addChild(allDevices);
            if (!fViewer.getControl().isDisposed()) {
                fViewer.setInput(root);
            }
        } else {
            if (!fViewer.getControl().isDisposed()) {
                fViewer.setInput(null);
            }
        }
    }
}
