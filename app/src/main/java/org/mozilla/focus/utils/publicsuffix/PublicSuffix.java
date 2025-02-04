/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.focus.utils.publicsuffix;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Helper methods for the public suffix part of a domain.
 *
 * A "public suffix" is one under which Internet users can (or historically could) directly register
 * names. Some examples of public suffixes are .com, .co.uk and pvt.k12.ma.us.
 *
 * https://publicsuffix.org/
 *
 * Some parts of the implementation of this class are based on InternetDomainName class of the Guava
 * project: https://github.com/google/guava
 */
public class PublicSuffix {

    public static void init(Context context) {
        PublicSuffixKt.init(context);
    }

    /**
     * Strip the public suffix from the domain. Returns the original domain if no public suffix
     * could be found.
     *
     * www.mozilla.org -&gt; www.mozilla
     * independent.co.uk -&gt; independent
     */
    @NonNull
    @WorkerThread // This method might need to load data from disk
    public static String stripPublicSuffix(Context context, @NonNull String domain) {
        if (domain.length() == 0) {
            return domain;
        }

        final int index = findPublicSuffixIndex(context, domain);
        if (index == -1) {
            return domain;
        }

        return domain.substring(0, index);
    }

    /**
     * Returns the public suffix with the specified number of additional parts.
     *
     * For example, the public suffix of "www.m.bbc.co.uk" (with 0 additional parts) is "co.uk".
     * With 1 additional part: "bbc.co.uk".
     *
     * @throws IllegalArgumentException if additionalPartCount is less than zero.
     * @throws NullPointerException if the Context or domain are null.
     * @return the public suffix with the specified number of additional parts, or the empty string if a public suffix does not exist.
     */
    @NonNull
    @WorkerThread // This method might need to load data from disk
    public static String getPublicSuffix(@NonNull final Context context, @NonNull final String domain, final int additionalPartCount) {
        if (context == null) {
            throw new NullPointerException("Expected non-null Context argument");
        }
        if (domain == null) {
            throw new NullPointerException("Expected non-null domain argument");
        }

        if (additionalPartCount < 0) {
            throw new IllegalArgumentException("Expected additionalPartCount > 0. Got: " + additionalPartCount);
        }

        final int publicSuffixCombinedIndex = findPublicSuffixIndex(context, domain);
        if (publicSuffixCombinedIndex < 0) {
            return "";
        }

        final String publicSuffix = domain.substring(publicSuffixCombinedIndex + 1); // +1 to remove prefix ".".

        final int nextPartIndex = publicSuffix.indexOf('.');
        final String publicSuffixFirstPart = nextPartIndex < 0 ? publicSuffix : publicSuffix.substring(0, nextPartIndex);

        final List<String> domainParts = normalizeAndSplit(domain);
        final int publicSuffixPartsIndex = domainParts.indexOf(publicSuffixFirstPart);
        final int returnedPartsIndex = Math.max(0, publicSuffixPartsIndex - additionalPartCount);
        return TextUtils.join(".", domainParts.subList(returnedPartsIndex, domainParts.size()));
    }

    /**
     * Returns the index of the leftmost part of the public suffix, or -1 if not found.
     */
    @WorkerThread
    private static int findPublicSuffixIndex(Context context, String domain) {
        final List<String> parts = normalizeAndSplit(domain);
        final int partsSize = parts.size();
        final Set<String> exact = PublicSuffixPatterns.getExactSet(context);

        for (int i = 0; i < partsSize; i++) {
            String ancestorName = TextUtils.join(".", parts.subList(i, partsSize));

            if (exact.contains(ancestorName)) {
                return joinIndex(parts, i);
            }

            // Excluded domains (e.g. !nhs.uk) use the next highest
            // domain as the effective public suffix (e.g. uk).
            if (PublicSuffixPatterns.EXCLUDED.contains(ancestorName)) {
                return joinIndex(parts, i + 1);
            }

            if (matchesWildcardPublicSuffix(ancestorName)) {
                return joinIndex(parts, i);
            }
        }

        return -1;
    }

    /**
     * Normalize domain and split into domain parts (www.mozilla.org -> [www, mozilla, org]).
     */
    private static List<String> normalizeAndSplit(String domain) {
        domain = domain.replaceAll("[.\u3002\uFF0E\uFF61]", "."); // All dot-like characters to '.'
        domain = domain.toLowerCase();

        if (domain.endsWith(".")) {
            domain = domain.substring(0, domain.length() - 1); // Strip trailing '.'
        }

        List<String> parts = new ArrayList<>();
        Collections.addAll(parts, domain.split("\\."));

        return parts;
    }

    /**
     * Translate the index of the leftmost part of the public suffix to the index of the domain string.
     *
     * [www, mozilla, org] and 2 => 12 (www.mozilla)
     */
    private static int joinIndex(List<String> parts, int index) {
        int actualIndex = parts.get(0).length();

        for (int i = 1; i < index; i++) {
            actualIndex += parts.get(i).length() + 1; // Add one for the "." that is not part of the list elements
        }

        return actualIndex;
    }

    /**
     * Does the domain name match one of the "wildcard" patterns (e.g. {@code "*.ar"})?
     */
    private static boolean matchesWildcardPublicSuffix(String domain) {
        final String[] pieces = domain.split("\\.", 2);
        return pieces.length == 2 && PublicSuffixPatterns.UNDER.contains(pieces[1]);
    }
}
