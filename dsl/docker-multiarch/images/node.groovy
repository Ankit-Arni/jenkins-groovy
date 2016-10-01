import vars.multiarch

for (arch in multiarch.allArches([
	// no upstream binaries available
	'armel',
])) {
	meta = multiarch.meta(getClass(), arch)

	freeStyleJob(meta.name) {
		description(meta.description)
		logRotator { daysToKeep(30) }
		label(meta.label)
		scm {
			git {
				remote { url('https://github.com/nodejs/docker-node.git') }
				branches('*/master')
				extensions {
					cleanAfterCheckout()
				}
			}
		}
		triggers {
			upstream("docker-${arch}-buildpack", 'UNSTABLE')
			scm('H H/6 * * *')
		}
		wrappers { colorizeOutput() }
		steps {
			shell(multiarch.templateArgs(meta, ['dpkgArch']) + '''
fromArch='linux-x64'
case "$dpkgArch" in
	armhf)
		toArch='linux-armv7l'
		;;
	i386)
		toArch='linux-x86'
		;;
	ppc64el)
		toArch='linux-ppc64le'
		;;
	s390x)
		toArch='linux-s390x'
		;;
	*)
		echo >&2 "unsupported architecture: $prefix"
		exit 1
		;;
esac

sed -i "s!$fromArch!$toArch!g" */{,*/}Dockerfile
sed -i "s!^FROM !FROM $prefix/!" */{,*/}Dockerfile

latest="$(./generate-stackbrew-library.sh | awk '$1 == "latest:" { print $3; exit }')"

for v in */; do
	v="${v%/}"
	[ -f "$v/Dockerfile" ] || continue
	docker build -t "$repo:$v" "$v"
	docker build -t "$repo:$v-onbuild" "$v/onbuild"
	docker build -t "$repo:$v-slim" "$v/slim"
	docker build -t "$repo:$v-wheezy" "$v/wheezy"
	if [ "$v" = "$latest" ]; then
		docker tag -f "$repo:$v" "$repo"
		docker tag -f "$repo:$v-onbuild" "$repo:onbuild"
		docker tag -f "$repo:$v-slim" "$repo:slim"
		docker tag -f "$repo:$v-wheezy" "$repo:wheezy"
	fi
done
''' + multiarch.templatePush(meta))
		}
	}
}