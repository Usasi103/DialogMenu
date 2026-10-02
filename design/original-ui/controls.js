function stoneFace(g,x,y,w,h,base) {
    // New deterministic, low contrast surface; not sampled from vanilla pixels.
    for(let yy=0;yy<h;yy++) for(let xx=0;xx<w;xx++) {
        const hash=((xx*73856093)^(yy*19349663)^0x23ab91)>>>0;
        const shade=base+(hash%9)-4;
        rect(g,x+xx,y+yy,1,1,`rgb(${shade},${shade},${shade})`);
    }
}

function frame(w,h,kind='normal',p=palettes[theme]) {
    return canvas(w,h,g=>{
        const light=p===palettes.light;
        if(kind==='panel') {
            rect(g,1,0,w-2,h,'#101010'); rect(g,0,1,w,h-2,'#101010');
            rect(g,1,1,w-2,h-2,light?'#555555':'#1b1b1b');
            rect(g,1,1,w-3,2,light?'#ffffff':'#858585');
            rect(g,1,1,2,h-3,light?'#ffffff':'#858585');
            rect(g,3,3,w-6,h-6,p.panel);
            rect(g,w-3,3,1,h-5,light?'#8b8b8b':'#222222');
            rect(g,3,h-3,w-5,1,light?'#8b8b8b':'#222222');
            rect(g,7,25,w-14,1,light?'#8b8b8b':'#222222');
            rect(g,7,26,w-14,1,light?'#ffffff':'#555555');
        } else {
            const selected=kind==='selected';
            rect(g,0,0,w,h,selected?'#ffffff':'#080808');
            rect(g,1,1,w-2,h-2,'#555555');
            rect(g,1,1,w-3,1,'#aaaaaa'); rect(g,1,1,1,h-3,'#aaaaaa');
            stoneFace(g,2,2,w-4,h-4,selected?119:111);
        }
    });
}

function checkMark(g,x,y,color) {
    [[0,2],[1,3],[2,4],[3,3],[4,2],[5,1],[6,0]].forEach(([dx,dy])=>rect(g,x+dx,y+dy,1,2,color));
}

function switchSkin(state,p=palettes[theme]) {
    return canvas(36,18,g=>{
        rect(g,0,1,36,16,'#101010'); rect(g,1,2,34,14,'#555555');
        rect(g,2,3,32,12,'#262626'); rect(g,2,14,32,1,'#8b8b8b');
        if(state===null) {rect(g,14,8,8,2,'#686868');return;}
        const x=state?20:2;
        g.drawImage(frame(14,16,'normal',p),x,1);
        if(state) {checkMark(g,x+4,6,'#101010');checkMark(g,x+3,5,'#b9ec82');rect(g,6,8,8,2,'#5f9138');}
        else {rect(g,x+4,8,6,2,'#303030');rect(g,25,8,6,2,'#555555');}
    });
}

function sliderSkin(selected,p=palettes[theme]) {
    return canvas(120,18,g=>{
        rect(g,0,0,120,18,'#080808'); rect(g,1,1,118,16,'#252525');
        rect(g,1,16,118,1,'#555555');
        const centers=[8,42,77,111];
        centers.forEach(x=>rect(g,x,12,1,3,'#777777'));
        const x=centers[selected]-4;
        g.drawImage(frame(8,18,'normal',p),x,0);
        rect(g,x+3,5,1,8,'#b9b9b9'); rect(g,x+4,5,1,8,'#555555');
    });
}
