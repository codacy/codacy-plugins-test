            if [ "${CIRCLE_BRANCH}" != "master" ]; then
              GHR_FLAGS+="-prerelease"
            fi
