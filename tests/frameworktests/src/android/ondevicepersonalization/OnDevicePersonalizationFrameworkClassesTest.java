/*
 * Copyright 2022 The Android Open Source Project
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

package android.ondevicepersonalization;

import static org.junit.Assert.assertEquals;

import android.ondevicepersonalization.rtb.Banner;
import android.ondevicepersonalization.rtb.Bid;
import android.ondevicepersonalization.rtb.BidRequest;
import android.ondevicepersonalization.rtb.BidResponse;
import android.ondevicepersonalization.rtb.Imp;
import android.ondevicepersonalization.rtb.SeatBid;
import android.os.Parcel;
import android.os.PersistableBundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit Tests of Framework API Classes.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class OnDevicePersonalizationFrameworkClassesTest {
    /**
     * Tests that the BidResponse object serializes correctly.
     */
    @Test
    public void testBidResponse() {
        Bid.Builder builder = new Bid.Builder();
        builder.setKey("key1");
        builder.setPrice(10.0);
        builder.setAdm("[adm]");
        builder.setAdomain("example.com");

        PersistableBundle ext = new PersistableBundle();
        ext.putInt("key2", 100);

        SeatBid.Builder seatBidBuilder = new SeatBid.Builder();
        seatBidBuilder.setSeat("seat10");
        seatBidBuilder.setExt(ext);
        seatBidBuilder.addBids(builder.build());

        BidResponse.Builder bidResponseBuilder = new BidResponse.Builder();
        bidResponseBuilder.addSeatBids(seatBidBuilder.build());
        bidResponseBuilder.setCur("abc");
        bidResponseBuilder.setNbr(10);
        bidResponseBuilder.setExt(ext);
        BidResponse response = bidResponseBuilder.build();

        assertEquals("abc", response.getCur());
        Bid bid = response.getSeatBids().get(0).getBids().get(0);
        assertEquals("key1", bid.getKey());
        assertEquals(10.0, bid.getPrice(), 0.01);
        assertEquals("[adm]", bid.getAdm());
        assertEquals("example.com", bid.getAdomain());

        Parcel parcel = Parcel.obtain();
        response.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        BidResponse response2 = BidResponse.CREATOR.createFromParcel(parcel);

        assertEquals("abc", response2.getCur());
        assertEquals(100, response2.getExt().getInt("key2"));
        assertEquals(100, response2.getSeatBids().get(0).getExt().getInt("key2"));
        assertEquals("seat10", response2.getSeatBids().get(0).getSeat());
        Bid bid2 = response2.getSeatBids().get(0).getBids().get(0);
        assertEquals("key1", bid2.getKey());
        assertEquals(10.0, bid2.getPrice(), 0.01);
        assertEquals("[adm]", bid2.getAdm());
        assertEquals("example.com", bid2.getAdomain());
    }

    /**
     * Tests that the BidRequest object serializes correctly.
     */
    @Test
    public void testBidRequest() {
        Banner.Builder builder = new Banner.Builder();
        builder.setW(100);
        builder.setH(200);

        Imp.Builder impBuilder = new Imp.Builder();
        impBuilder.setBanner(builder.build());

        BidRequest.Builder bidRequestBuilder = new BidRequest.Builder();
        bidRequestBuilder.addImps(impBuilder.build());
        BidRequest request = bidRequestBuilder.build();

        Banner banner = request.getImps().get(0).getBanner();
        assertEquals(100, banner.getW());
        assertEquals(200, banner.getH());

        Parcel parcel = Parcel.obtain();
        request.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        BidRequest request2 = BidRequest.CREATOR.createFromParcel(parcel);

        assertEquals(request, request2);
        Banner banner2 = request2.getImps().get(0).getBanner();
        assertEquals(100, banner2.getW());
        assertEquals(200, banner2.getH());
    }

    /**
     * Tests that the ExchangeResult object serializes correctly.
     */
    @Test
    public void testExchangeResult() {
        ExchangeResult result =
                new ExchangeResult.Builder()
                        .setKey("key1")
                        .setResponse("content")
                        .build();

        Parcel parcel = Parcel.obtain();
        result.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ExchangeResult result2 = ExchangeResult.CREATOR.createFromParcel(parcel);

        assertEquals(result, result2);
        assertEquals("key1", result2.getKey());
        assertEquals("content", result2.getResponse());
    }

    /**
     * Tests that the PackageId object serializes correctly.
     */
    @Test
    public void testPackageId() {
        PackageId packageId =
                new PackageId.Builder().setPackageName("x.y.z").setCertDigest("abc123").build();

        Parcel parcel = Parcel.obtain();
        packageId.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        PackageId packageId2 = PackageId.CREATOR.createFromParcel(parcel);

        assertEquals(packageId, packageId2);
        assertEquals("x.y.z", packageId2.getPackageName());
        assertEquals("abc123", packageId2.getCertDigest());
    }
}
