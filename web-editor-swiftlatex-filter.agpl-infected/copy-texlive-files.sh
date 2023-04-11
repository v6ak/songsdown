#!/bin/bash
# safety settings
set -u
set -e
set -o pipefail

missing=0
while read filename; do
	if [ "${filename:0:1}" == "#" ] || [ "$filename" == "" ]; then
		# We can't filter it with grep, as this would run in a subshell, which would break the `missing` variable.
		continue
	fi
	base=$(basename $filename)
	format=$(basename "$(dirname $filename)")
	tool=$(basename "$(dirname "$(dirname $filename)")")
	if [ "$tool" == pk ]; then
		is_pk=1
		tool=$(basename "$(dirname "$(dirname "$(dirname $filename)")")")
		out_dir="$tool/pk/$format"
	else
		is_pk=0
		out_dir="$tool/$format"
	fi
	if [ "$base" == swiftlatexpdftex.fmt ]; then
		continue
	fi
	case $format in
		11)
			source=/var/lib/texmf/fonts/map/pdftex/updmap/$base
			;;
		26|32)
			source=$(find /usr/share/texlive/texmf-dist/{fonts,tex/{latex,generic,context}} -name "$base")
			;;
		3)
			source=$(find /usr/share/texlive/texmf-dist/fonts/tfm/ -name "$base.tfm")
			;;
		600)
			#source=$(find /root/.texlive2019/texmf-var/fonts/pk -name "$base.600pk")
			source=$(kpsewhich -engine=pdflatex "$base" -format=pk -mktex=pk)
			;;
		33)
			# Those are missing, and don't seem to be needed
			continue
			;;
		*)
			echo "## $base ($format) – unknown source"
			find / -name "$base" | xargs sha256sum
			missing=1
			source="-"
			;;
	esac
	
	if [ "$source" != "-" ] && [ ! -e "$source" ]; then
		echo "Source doesn't exist: $source"
		missing=1
	fi
	if [ "$source" == "" ]; then
		echo "## $base ($format) – missing"
		find / -name "$base" | xargs sha256sum
		missing=1
	fi
	if [ "$missing" == 0 ]; then
		mkdir -p "/out/$out_dir"
		cp "$source" "/out/$out_dir/$base"
	fi
done

exit $missing
