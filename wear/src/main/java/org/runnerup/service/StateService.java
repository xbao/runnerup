/*
 * Copyright (C) 2014 weides@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.runnerup.service;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import org.runnerup.common.util.Constants;

import java.util.HashSet;

import static com.google.android.gms.wearable.PutDataRequest.WEAR_URI_SCHEME;

public class StateService extends Service implements NodeApi.NodeListener, MessageApi.MessageListener, DataApi.DataListener {

    public static final String UPDATE_TIME = "UPDATE_TIME";

    private final IBinder mBinder = new LocalBinder();
    private GoogleApiClient mGoogleApiClient;
    private HashSet<Node> connectedNodes = new HashSet<Node>();

    private String phoneNode;
    private Node localNode;

    private Bundle data;
    private long dataUpdateTime;
    private Bundle headers;

    @Override
    public void onCreate() {
        super.onCreate();

        mGoogleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {

                        Wearable.NodeApi.addListener(mGoogleApiClient, StateService.this);
                        Wearable.MessageApi.addListener(mGoogleApiClient, StateService.this);
                        Wearable.DataApi.addListener(mGoogleApiClient, StateService.this);

                        /* get our own node id */
                        Wearable.NodeApi.getLocalNode(mGoogleApiClient).setResultCallback(
                                getLocalNodeResultCallback);

                        /** get info about connected nodes in background */
                        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).setResultCallback(
                                new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                                    @Override
                                    public void onResult(NodeApi.GetConnectedNodesResult nodes) {
                                        System.err.println("onGetConnectedNodes");
                                        for (Node n : nodes.getNodes()) {
                                            onPeerConnected(n);
                                        }
                                    }
                                });
                    }

                    @Override
                    public void onConnectionSuspended(int cause) {
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                    }
                })
                .addApi(Wearable.API)
                .build();
        mGoogleApiClient.connect();

        System.err.println("StateService.onCreate()");
    }

    private boolean checkConnection() {
        return mGoogleApiClient != null && mGoogleApiClient.isConnected();
    }

    @Override
    public void onDestroy() {
        System.err.println("StateService.onDestroy()");

        if (mGoogleApiClient != null) {
            if (mGoogleApiClient.isConnected()) {
                phoneNode = null;
                connectedNodes.clear();

                /* delete our node id */
                Wearable.DataApi.deleteDataItems(mGoogleApiClient,
                        new Uri.Builder().scheme(WEAR_URI_SCHEME).path(
                                Constants.Wear.Path.WEAR_NODE_ID).build());

                Wearable.NodeApi.removeListener(mGoogleApiClient, this);
                Wearable.MessageApi.removeListener(mGoogleApiClient, this);
                Wearable.DataApi.removeListener(mGoogleApiClient, this);
            }
            mGoogleApiClient.disconnect();
            mGoogleApiClient = null;
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class LocalBinder extends android.os.Binder {
        public StateService getService() {
            return StateService.this;
        }
    }

    Bundle getBundle(Bundle src, long lastUpdateTime) {
        if (src == null)
            return null;

        long updateTime = src.getLong(UPDATE_TIME, 0);
        if (lastUpdateTime >= updateTime)
            return null;

        Bundle b = new Bundle();
        b.putAll(src);
        return b;
    }

    public Bundle getHeaders(long lastUpdateTime) {
        return getBundle(headers, lastUpdateTime);
    }

    public Bundle getData(long lastUpdateTime) {
        return getBundle(data, lastUpdateTime);
    }

    @Override
    public void onPeerConnected(Node node) {
        System.err.println("onPeerConnected: " + node.getDisplayName() + ", " + node.getId());
        connectedNodes.add(node);
    }

    @Override
    public void onPeerDisconnected(Node node) {
        System.err.println("onPeerDisconnected: " + node.getDisplayName() + ", " + node.getId());
        connectedNodes.remove(node);
        if (node.getId().contentEquals(phoneNode))
            phoneNode = null;
    }

    private ResultCallback<NodeApi.GetLocalNodeResult> getLocalNodeResultCallback =
            new ResultCallback<NodeApi.GetLocalNodeResult>() {
        @Override
        public void onResult(NodeApi.GetLocalNodeResult getLocalNodeResult) {
            if (!checkConnection())
                return;

            localNode = getLocalNodeResult.getNode();
            Wearable.DataApi.putDataItem(mGoogleApiClient,
                    PutDataRequest.create(Constants.Wear.Path.WEAR_NODE_ID).
                            setData(getLocalNodeResult.getNode().getId().getBytes()));
        }
    };

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        System.err.println("onMessageReceived: " + messageEvent);
        if (Constants.Wear.Path.MSG_WORKOUT_EVENT.contentEquals(messageEvent.getPath())) {
            data = DataMap.fromByteArray(messageEvent.getData()).toBundle();
            data.putLong(UPDATE_TIME, System.currentTimeMillis());
        }
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        for (DataEvent ev : dataEvents) {
            System.err.println("onDataChanged: " + ev.getDataItem().getUri());
            String path = ev.getDataItem().getUri().getPath();
            if (Constants.Wear.Path.PHONE_NODE_ID.contentEquals(path)) {
                setPhoneNode(ev);
            } else if (Constants.Wear.Path.HEADERS.contentEquals(path)) {
                setHeaders(ev);
            }
        }
    }

    public void setPhoneNode(DataEvent ev) {
        if (ev.getType() == DataEvent.TYPE_CHANGED) {
            phoneNode = new String(ev.getDataItem().getData());
        } else if (ev.getType() == DataEvent.TYPE_DELETED) {
            phoneNode = null;
        }
    }

    private void setHeaders(DataEvent ev) {
        if (ev.getType() == DataEvent.TYPE_CHANGED) {
            headers = DataMapItem.fromDataItem(ev.getDataItem()).getDataMap().toBundle();
            headers.putLong(UPDATE_TIME, System.currentTimeMillis());
        } else {
            headers = null;
        }
    }
}
