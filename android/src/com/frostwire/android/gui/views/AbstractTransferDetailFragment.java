/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android.gui.views;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.gui.util.TransferStateStrings;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.Sha1Hash;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.util.Logger;

import java.text.DecimalFormatSymbols;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 *         Created on 10/10/17.
 */


public abstract class AbstractTransferDetailFragment extends AbstractFragment implements TimerObserver {
    private static Logger LOG = Logger.getLogger(AbstractTransferDetailFragment.class);

    private static String INFINITY = null;
    protected final TransferStateStrings transferStateStrings;

    private String tabTitle;
    protected UIBittorrentDownload uiBittorrentDownload;
    protected TorrentHandle torrentHandle;
    private TextView detailProgressTitleTextView;
    private ProgressBar detailProgressProgressBar;
    protected TimerSubscription subscription;
    private TextView detailProgressStatusTextView;
    private TextView detailProgressDownSpeedTextView;
    private TextView detailProgressUpSpeedTextView;

    public AbstractTransferDetailFragment(int layoutId) {
        super(layoutId);
        setHasOptionsMenu(true);
        // we can pass null below since this map has already been initialized by TransferListAdapter
        transferStateStrings = TransferStateStrings.getInstance(null);
    }

    public String getTabTitle() {
        return tabTitle;
    }

    public AbstractTransferDetailFragment init(String tabTitle, UIBittorrentDownload uiBittorrentDownload) {
        this.tabTitle = tabTitle;
        this.uiBittorrentDownload = uiBittorrentDownload;
        ensureTorrentHandle();
        return this;
    }

    @Override
    protected void initComponents(View rootView, Bundle savedInstanceState) {
        super.initComponents(rootView, savedInstanceState);
        initDetailProgress(rootView);
        updateDetailProgress(uiBittorrentDownload);
    }

    /**
     * This is a common section at the top of all the detail fragments
     * which contains the title of the transfer and the current progress
     */
    protected void initDetailProgress(View v) {
        detailProgressTitleTextView = findView(v, R.id.view_transfer_detail_progress_title);
        detailProgressProgressBar = findView(v, R.id.view_transfer_detail_progress_progress);
        detailProgressStatusTextView = findView(v, R.id.view_transfer_detail_progress_status);
        detailProgressDownSpeedTextView = findView(v, R.id.view_transfer_detail_progress_down_speed);
        detailProgressUpSpeedTextView = findView(v, R.id.view_transfer_detail_progress_up_speed);
    }

    protected void updateDetailProgress(UIBittorrentDownload uiBittorrentDownload) {
        if (uiBittorrentDownload == null) {
            return;
        }
        if (detailProgressTitleTextView != null) {
            detailProgressTitleTextView.setText(uiBittorrentDownload.getDisplayName());
        }
        if (detailProgressProgressBar != null) {
            detailProgressProgressBar.setProgress(uiBittorrentDownload.getProgress());
        }
        if (detailProgressStatusTextView != null) {
            detailProgressStatusTextView.setText(transferStateStrings.get(uiBittorrentDownload.getState()));
        }
        if (detailProgressDownSpeedTextView != null) {
            detailProgressDownSpeedTextView.setText(UIUtils.getBytesInHuman(uiBittorrentDownload.getDownloadSpeed()) + "/s");
        }
        if (detailProgressUpSpeedTextView != null) {
            detailProgressUpSpeedTextView.setText(UIUtils.getBytesInHuman(uiBittorrentDownload.getUploadSpeed()) + "/s");
        }
    }

    @Override
    public void onTime() {
        if (!isVisible() || !isAdded()) {
            return;
        }
        if (uiBittorrentDownload == null) {
            Activity activity = getActivity();
            if (activity != null) {
                Intent intent = activity.getIntent();
                if (intent != null) {
                    String infoHash = intent.getStringExtra("infoHash");
                    recoverUIBittorrentDownload(infoHash);
                }
            }

            if (uiBittorrentDownload == null) {
                return;
            }
        }
        updateDetailProgress(uiBittorrentDownload);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
        subscription = TimerService.subscribe(this, 2);
        if (uiBittorrentDownload == null) {
            return;
        }
        ensureTorrentHandle();
        updateDetailProgress(uiBittorrentDownload);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (subscription != null) {
            subscription.unsubscribe();
        }
    }
    // Fragment State serialization = onSaveInstanceState
    // Fragment State deserialization = onActivityCreated

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (uiBittorrentDownload != null) {
            outState.putString("infohash", uiBittorrentDownload.getInfoHash());
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (uiBittorrentDownload == null && savedInstanceState != null) {
            String infoHash = savedInstanceState.getString("infohash");
            recoverUIBittorrentDownload(infoHash);
        }
    }

    private void recoverUIBittorrentDownload(String infoHash) {
        if (infoHash != null) {
            BittorrentDownload bittorrentDownload = TransferManager.instance().getBittorrentDownload(infoHash);
            if (bittorrentDownload instanceof UIBittorrentDownload) {
                uiBittorrentDownload = (UIBittorrentDownload) bittorrentDownload;
                ensureTorrentHandle();
            }
        }
    }

    private void ensureTorrentHandle() {
        if (torrentHandle == null && uiBittorrentDownload != null) {
            torrentHandle = uiBittorrentDownload.getDl().getTorrentHandle();
            if (torrentHandle == null) {
                torrentHandle = BTEngine.getInstance().find(new Sha1Hash(uiBittorrentDownload.getInfoHash()));
            }
        }
    }

    // All utility functions will be here for now

    /**
     * Converts a value in seconds to:
     * "d:hh:mm:ss" where d=days, hh=hours, mm=minutes, ss=seconds, or
     * "h:mm:ss" where h=hours<24, mm=minutes, ss=seconds, or
     * "m:ss" where m=minutes<60, ss=seconds
     */
    public static String seconds2time(long seconds) {
        if (seconds == -1) {
            if (INFINITY == null) {
                INFINITY = DecimalFormatSymbols.getInstance().getInfinity();
            }
            return INFINITY;
        }
        long minutes = seconds / 60;
        seconds = seconds - minutes * 60;
        long hours = minutes / 60;
        minutes = minutes - hours * 60;
        long days = hours / 24;
        hours = hours - days * 24;
        // build the numbers into a string
        StringBuilder time = new StringBuilder();
        if (days != 0) {
            time.append(Long.toString(days));
            time.append(":");
            if (hours < 10)
                time.append("0");
        }
        if (days != 0 || hours != 0) {
            time.append(Long.toString(hours));
            time.append(":");
            if (minutes < 10)
                time.append("0");
        }
        time.append(Long.toString(minutes));
        time.append(":");
        if (seconds < 10)
            time.append("0");
        time.append(Long.toString(seconds));
        return time.toString();
    }

    public static String getShareRatio(UIBittorrentDownload dl) {
        long sent = dl.getBytesSent();
        long received = dl.getBytesReceived();
        if (received < 0) {
            return "0%";
        }
        return String.valueOf(100 * ((float) sent / (float) received)) + "%";
    }
}