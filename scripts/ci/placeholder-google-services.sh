#!/usr/bin/env bash
# Writes the placeholder app/google-services.json that CI and code scanning build with. The google-services Gradle
# plugin fails the build without the file; CI only compiles and runs JVM tests against fake repositories, so the
# right shape and package name are enough. The real file is never committed (releases get it from a secret).
set -euo pipefail
cd "$(dirname "$0")/../.."
# Split so the repository never holds a string shaped like a real API key.
placeholder_key="AIza""PlaceholderPlaceholderPlaceholderPlace"
cat > app/google-services.json <<JSON
{
  "project_info": {
    "project_number": "000000000000",
    "project_id": "fintrack-ci-placeholder",
    "storage_bucket": "fintrack-ci-placeholder.appspot.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:000000000000:android:0000000000000000000000",
        "android_client_info": {
          "package_name": "com.joaoeoneves.fintrack"
        }
      },
      "oauth_client": [
        {
          "client_id": "000000000000-placeholderplaceholderplacehold.apps.googleusercontent.com",
          "client_type": 3
        }
      ],
      "api_key": [
        {
          "current_key": "${placeholder_key}"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": []
        }
      }
    }
  ],
  "configuration_version": "1"
}
JSON
