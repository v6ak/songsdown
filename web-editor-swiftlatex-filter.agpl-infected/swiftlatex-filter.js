(function(){
    //const TEXLIVE_FILES_TO_PREFETCH=[];  // disables prefetch
    const enginePromise = (async () => {
		// When the files aren't served from https://github.com/SwiftLaTeX/Texlive-Ondemand,
		// we might not get the fileid header, which makes SwiftLaTeX fail. This can be worked around by
		// file prefetching.
		// It also reduces the number of round-trips, which makes the initial load faster.
		const latexFilesFetches = TEXLIVE_FILES_TO_PREFETCH.map(async name => {
			const response = await fetch(name);
			const bytes = await response.arrayBuffer();
			console.log("fetch", name, bytes, new Uint8Array(bytes));
			const baseName = name.split('/').pop();
			const fullBaseName = (name.startsWith("pdftex/3/"))
				? baseName + ".tfm"
				: (name.startsWith("pdftex/pk/600/")
					? baseName + ".600pk"
					: baseName
				);
			return {
				name: fullBaseName,
				fullPath: name,
				data: response.ok ? new Uint8Array(bytes) : null
			}
		});

        // XeTeX would be probably better for locale-specific things etc. However, it has some cons:
        // * speed
        // * XeTeXEngine produces xdv instead of pdf, which is hard to handle
        //const engine = new XeTeXEngine();
        const engine = new PdfTeXEngine();
        await engine.loadEngine();
        for(const i in latexFilesFetches){
			const file = await latexFilesFetches[i];
			console.log("file", file);
			if (file.data) {
				engine.writeMemFSFile(file.name, file.data);
			}
		}
        console.log("latexFiles", latexFilesFetches);
        engine.writeMemFSFile("songs.sty", LATEX_SONGS_STY);

        // Don't load from https://texlive2.swiftlatex.com/ (broken at 2023-04-11)
        const nonExistentUrlPrefix = URL.createObjectURL(new Blob([]));
        engine.setTexliveEndpoint(nonExistentUrlPrefix + "/x");
        // You can use this when you run https://github.com/SwiftLaTeX/Texlive-Ondemand
        // However, you probably want it only for development.
        // engine.setTexliveEndpoint("http://localhost:5001");

        engine.setEngineMainFile("main.tex");

        return engine;
    })();
    
    
    let lastUrl = null;
    
    window.songsdownFilter = async (texData) => {
        const engine = await enginePromise;
        const mainTex = LATEX_SONGS_TEMPLATE.replace("\\includegeneratedcontent", texData);
        engine.writeMemFSFile("main.tex", mainTex);
        
        let r = await engine.compileLaTeX();
        if (!r.pdf) {
            throw new Error("Cannot process: LaTeX engine returned " + r.status + " with output:\n"+r.log)
        }
        const blob = new Blob([r.pdf], { type: 'application/pdf' });
        
        const url = URL.createObjectURL(blob);        
        if(lastUrl) {
            // release resources
            URL.revokeObjectURL(lastUrl);
        }
        lastUrl = url;
        return url;
    }
})();
