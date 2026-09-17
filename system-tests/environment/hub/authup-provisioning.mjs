// FLAME Hub's permission set, declared so Authup can resolve it.
//
// Since Hub 0.13.0 the authorization middleware genuinely enforces permissions
// (it used to run in dry-run mode), and Hub does not register them itself. Each
// name is declared as a top-level permission so Authup's built-in `admin` role,
// which holds `globalPermissions: ['*']`, resolves them at provisioning time —
// which is what puts them in the admin token this suite authenticates with.
//
// Mirrors `buildAuthupProvisioning()` in @privateaim/server-test-kit. Keep in
// sync with `PermissionName` in @privateaim/kit.
export default {
    "permissions": [
        {
            "attributes": {
                "name": "event_create"
            }
        },
        {
            "attributes": {
                "name": "event_read"
            }
        },
        {
            "attributes": {
                "name": "event_delete"
            }
        },
        {
            "attributes": {
                "name": "bucket_create"
            }
        },
        {
            "attributes": {
                "name": "bucket_update"
            }
        },
        {
            "attributes": {
                "name": "bucket_delete"
            }
        },
        {
            "attributes": {
                "name": "log_create"
            }
        },
        {
            "attributes": {
                "name": "log_delete"
            }
        },
        {
            "attributes": {
                "name": "log_read"
            }
        },
        {
            "attributes": {
                "name": "project_create"
            }
        },
        {
            "attributes": {
                "name": "project_delete"
            }
        },
        {
            "attributes": {
                "name": "project_update"
            }
        },
        {
            "attributes": {
                "name": "project_approve"
            }
        },
        {
            "attributes": {
                "name": "registry_manage"
            }
        },
        {
            "attributes": {
                "name": "registry_project_manage"
            }
        },
        {
            "attributes": {
                "name": "node_create"
            }
        },
        {
            "attributes": {
                "name": "node_delete"
            }
        },
        {
            "attributes": {
                "name": "node_update"
            }
        },
        {
            "attributes": {
                "name": "analysis_approve"
            }
        },
        {
            "attributes": {
                "name": "analysis_update"
            }
        },
        {
            "attributes": {
                "name": "analysis_create"
            }
        },
        {
            "attributes": {
                "name": "analysis_execution_start"
            }
        },
        {
            "attributes": {
                "name": "analysis_execution_stop"
            }
        },
        {
            "attributes": {
                "name": "analysis_delete"
            }
        },
        {
            "attributes": {
                "name": "analysis_result_read"
            }
        },
        {
            "attributes": {
                "name": "analysis_self_message_broker_use"
            }
        },
        {
            "attributes": {
                "name": "analysis_self_storage_use"
            }
        },
        {
            "attributes": {
                "name": "master_image_manage"
            }
        },
        {
            "attributes": {
                "name": "master_image_group_manage"
            }
        },
        {
            "attributes": {
                "name": "service_manage"
            }
        }
    ]
};
