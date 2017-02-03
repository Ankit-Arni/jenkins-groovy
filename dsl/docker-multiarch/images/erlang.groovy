import vars.multiarch

for (arch in multiarch.allArches()) {
	meta = multiarch.meta(getClass(), arch)

	freeStyleJob(meta.name) {
		description(meta.description)
		logRotator { daysToKeep(30) }
		concurrentBuild(false)
		label(meta.label)
		scm {
			git {
				remote { url('https://github.com/c0b/docker-erlang-otp.git') }
				branches('*/master')
				extensions {
					cleanAfterCheckout()
				}
			}
		}
		triggers {
			//upstream("docker-${arch}-debian, docker-${arch}-buildpack", 'UNSTABLE')
			scm('H H/6 * * *')
		}
		wrappers { colorizeOutput() }
		steps {
			shell(multiarch.templateArgs(meta, ['gnuArch']) + '''
# ignore ancient versions
rm -r R*/ elixir/

# we're not ready for this yet
rm -r 19/

sed -i "s!^FROM !FROM $prefix/!" */{,*/}Dockerfile

# explicitly set the gcc arch tuple to the arch of gcc from the build environment
# (this makes sure our armhf build on arm64 hardware builds an armhf gcc)
sed -i "s!/configure !/configure --build='$gnuArch' !g" */Dockerfile

# update autoconf config.guess and config.sub so they support our architectures for sure
sed -i 's!.* autoconf !\\t\\&\\& ( cd erts/autoconf \\&\\& curl -fSL "http://git.savannah.gnu.org/gitweb/?p=config.git;a=blob_plain;f=config.guess;hb=HEAD" -o config.guess \\&\\& curl -fSL "http://git.savannah.gnu.org/gitweb/?p=config.git;a=blob_plain;f=config.sub;hb=HEAD" -o config.sub ) \\\\\\n&!' */{,*/}Dockerfile

latest='18' # TODO discover this automatically somehow

for v in */; do
	v="${v%/}"
	for variant in '' onbuild slim; do
		if [ -f "$v/$variant/Dockerfile" ]; then
			docker build -t "$repo:$v${variant:+-$variant}" "$v/$variant"
			pushImages+=( "$repo:$v${variant:+-$variant}" )
			if [ "$v" = "$latest" ]; then
				docker tag "$repo:$v${variant:+-$variant}" "$repo${variant:+:$variant}"
				pushImages+=( "$repo${variant:+:$variant}" )
			fi
		fi
	done
done
''' + multiarch.templatePush(meta))
		}
	}
}
