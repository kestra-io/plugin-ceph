package io.kestra.plugin.ceph.it;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.ceph.rgw.CreateBucket;
import io.kestra.plugin.ceph.rgw.CreateUser;
import io.kestra.plugin.ceph.rgw.DeleteBucket;
import io.kestra.plugin.ceph.rgw.ListBuckets;
import io.kestra.plugin.ceph.rgw.ListUsers;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

/**
 * RGW user and bucket lifecycle against a live Ceph cluster (see {@code .github/setup-unit.sh}).
 * Skipped unless {@code CEPH_IT=true}. The bucket is cleaned up via {@code DeleteBucket}, but this
 * plugin has no RGW user-deletion task, so the created user is left behind; the demo cluster is
 * fully torn down by {@code cleanup-unit.sh} after the CI run, and the uid is randomized per run,
 * so this does not leak into or affect subsequent runs.
 */
@KestraTest
@EnabledIfEnvironmentVariable(named = "CEPH_IT", matches = "true")
class RgwIT {

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void rgwLifecycle() throws Exception {
        var runContext = runContextFactory.of();
        var uid = "it-user-" + System.nanoTime();
        var bucketName = "it-bucket-" + System.nanoTime();

        var user = CephIT.withConnection(CreateUser.builder().id("rgwItCreateUser" + System.nanoTime()).type(CreateUser.class.getName()))
            .uid(Property.ofValue(uid))
            .displayName(Property.ofValue("Ceph plugin IT user"))
            .build()
            .run(runContext);
        assertThat(user.userId(), is(uid));

        var users = Eventually.eventually(
            () -> CephIT.withConnection(ListUsers.builder().id("rgwItListUsers" + System.nanoTime()).type(ListUsers.class.getName()))
                .build()
                .run(runContext),
            u -> u.getUsers().contains(uid),
            10,
            1000L
        );
        assertThat(users.getUsers(), hasItem(uid));

        var bucket = CephIT.withConnection(CreateBucket.builder().id("rgwItCreateBucket" + System.nanoTime()).type(CreateBucket.class.getName()))
            .bucketName(Property.ofValue(bucketName))
            .owner(Property.ofValue(uid))
            .build()
            .run(runContext);
        assertThat(bucket.name(), is(bucketName));

        try {
            var buckets = Eventually.eventually(
                () -> CephIT.withConnection(ListBuckets.builder().id("rgwItListBuckets" + System.nanoTime()).type(ListBuckets.class.getName()))
                    .build()
                    .run(runContext),
                b -> b.getBuckets().contains(bucketName),
                10,
                1000L
            );
            assertThat(buckets.getBuckets(), hasItem(bucketName));
        } finally {
            // Delete the (empty) test bucket via the default path. purgeObjects=true exercises the
            // Dashboard's object-purge path, which returns a 500 on a single-node RGW and is not in
            // the documented reef delete API, so it is left for a separate follow-up.
            var deleted = CephIT.withConnection(DeleteBucket.builder().id("rgwItDeleteBucket" + System.nanoTime()).type(DeleteBucket.class.getName()))
                .bucketName(Property.ofValue(bucketName))
                .build()
                .run(runContext);
            assertThat(deleted.getDeleted(), is(true));
        }
    }
}
