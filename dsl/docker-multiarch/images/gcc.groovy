def arches = [
	'arm64',
	'armel',
	'armhf',
	'ppc64le',
	's390x',
]

for (arch in arches) {
	freeStyleJob("docker-${arch}-gcc") {
		logRotator { daysToKeep(30) }
		label("docker-${arch}")
		scm {
			git {
				remote { url('https://github.com/docker-library/gcc.git') }
				branches('*/master')
				clean()
			}
		}
		triggers {
			upstream("docker-${arch}-buildpack", 'UNSTABLE')
			scm('H H/6 * * *')
		}
		wrappers { colorizeOutput() }
		steps {
			shell("""\
prefix='${arch}'
repo="\$prefix/gcc"

sed -i "s!^FROM !FROM \$prefix/!" */Dockerfile

for v in */; do
	v="\${v%/}"
	from="\$(awk '\$1 == "FROM" { print \$2; exit }' "\$v/Dockerfile")"
	if ! docker inspect "\$from" &> /dev/null; then
		echo >&2 "warning, '\$from' does not exist; skipping \$v"
		rm -r "\$v/"
	fi
done

latest="\$(./generate-stackbrew-library.sh | awk '\$1 == "latest:" { print \$3; exit }')"

for v in */; do
	v="\${v%/}"
	docker build -t "\$repo:\$v" "\$v"
	if [ "\$v" = "\$latest" ]; then
		docker tag -f "\$repo:\$v" "\$repo"
	fi
done

# we don't have /u/arm64
if [ '${arch}' != 'arm64' ]; then
	docker images "\$repo" \\
		| awk -F '  +' 'NR>1 { print \$1 ":" \$2 }' \\
		| xargs -rtn1 docker push
fi
""")
		}
	}
}