package io.kestra.plugin.ceph.rgw;

import io.kestra.core.models.tasks.common.EncryptedString;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Curated view of an RGW user returned by {@link CreateUser}. The S3 secret key is wrapped in an
 * {@link EncryptedString} so it is stored encrypted in the execution outputs, matching how the
 * AWS/GCP/Azure auth tasks expose generated credentials.
 */
public record UserInfo(
    @Schema(
        title = "User ID",
        description = "Unique identifier of the RGW user."
    )
    String userId,

    @Schema(
        title = "Display name",
        description = "Human-readable name of the user."
    )
    String displayName,

    @Schema(
        title = "Email",
        description = "Contact email of the user, if set."
    )
    String email,

    @Schema(
        title = "S3 access keys",
        description = "S3 credentials Ceph generated for the user. Creating the user is the only point " +
            "at which the secret key is returned."
    )
    List<S3Key> keys
) implements io.kestra.core.models.tasks.Output {

    /**
     * A single S3 key pair from the user's {@code keys} array. The secret key is encrypted in the
     * outputs and is decrypted automatically when a downstream task references it.
     */
    public record S3Key(
        @Schema(
            title = "Access key",
            description = "S3 access key ID for the user."
        )
        String accessKey,

        @Schema(
            title = "Secret key",
            description = "S3 secret access key, encrypted in the outputs. Reference it directly to feed " +
                "another task, or store it in a secret; avoid logging it."
        )
        EncryptedString secretKey
    ) {
    }
}
