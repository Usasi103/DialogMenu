// Original 16×16 sketches. Integer pixels only; no imported or traced images.
(function () {
    'use strict';
    function sprite(name, colors, paint) {
        const rows = Array.from({length:16},()=>Array(16).fill('.'));
        const pixel = (x,y,c) => {
            if(x<0||x>15||y<0||y>15)throw Error(name+': pixel outside 16x16');
            if(!(c in colors))throw Error(name+': undefined color '+c);
            rows[y][x]=c;
        };
        const box = (x,y,w,h,c) => {for(let yy=y;yy<y+h;yy++)for(let xx=x;xx<x+w;xx++)pixel(xx,yy,c)};
        const line = (x0,y0,x1,y1,c) => {
            let dx=Math.abs(x1-x0),sx=x0<x1?1:-1,dy=-Math.abs(y1-y0),sy=y0<y1?1:-1,e=dx+dy;
            while(true){pixel(x0,y0,c);if(x0===x1&&y0===y1)break;const e2=e*2;if(e2>=dy){e+=dy;x0+=sx}if(e2<=dx){e+=dx;y0+=sy}}
        };
        const poly = (points,c) => {
            for(let y=0;y<16;y++)for(let x=0;x<16;x++){
                const xx=x+.5,yy=y+.5;let inside=false;
                for(let i=0,j=points.length-1;i<points.length;j=i++){
                    const [xi,yi]=points[i],[xj,yj]=points[j];
                    if((yi>yy)!==(yj>yy)&&xx<(xj-xi)*(yy-yi)/(yj-yi)+xi)inside=!inside;
                }
                if(inside)pixel(x,y,c);
            }
        };
        const ellipse=(cx,cy,rx,ry,c)=>{for(let y=0;y<16;y++)for(let x=0;x<16;x++)if(((x-cx)/rx)**2+((y-cy)/ry)**2<=1)pixel(x,y,c)};
        paint({pixel,box,line,poly,ellipse});
        return {name,colors,rows:rows.map(r=>r.join(''))};
    }
    const icons={};
    icons.profile=sprite('玩家档案',{d:'#68543d',s:'#967650',m:'#bd9b6d',p:'#d9c096',h:'#f0ddba',i:'#88785e'},({pixel,box,line,poly})=>{
        poly([[3,3],[10,1],[14,4],[14,13],[4,15],[2,13],[2,4]],'d');
        poly([[3,4],[5,5],[5,14],[3,12]],'s');
        poly([[4,3],[10,2],[13,4],[13,12],[5,14],[5,5]],'m');
        poly([[5,4],[10,3],[12,5],[12,11],[6,12]],'p');
        poly([[6,4],[10,3],[10,6],[6,7]],'h');
        line(7,7,10,6,'i');line(7,9,10,8,'i');line(7,11,9,10,'i');
        line(6,12,11,11,'h');pixel(3,6,'p');pixel(3,10,'p');pixel(11,3,'s');pixel(11,4,'h');
    });
    icons.sound=sprite('双音符',{d:'#3e5d69',s:'#557d88',m:'#78a7ad',h:'#b8d8d1'},({pixel,box,line,poly,ellipse})=>{
        poly([[6,3],[14,1],[14,4],[8,6],[8,13],[6,13]],'d');
        box(12,3,2,8,'d');ellipse(4,12,3,2,'d');ellipse(10,10,3,2,'d');
        poly([[6,3],[13,1],[13,3],[7,5],[7,12],[6,12]],'m');
        box(12,4,1,6,'m');ellipse(4,11.5,2.6,1.3,'s');ellipse(10,9.5,2.6,1.3,'s');
        line(7,3,12,2,'h');line(6,5,6,9,'h');pixel(3,11,'h');pixel(4,11,'m');pixel(9,9,'h');pixel(10,9,'m');
    });
    icons.particles=sprite('叶片与微光',{d:'#426444',s:'#648452',m:'#8dac69',h:'#b8cd8b',g:'#d5c886',l:'#eee1ac'},({pixel,box,line,poly})=>{
        poly([[13,1],[14,6],[11,10],[6,12],[3,14],[4,9],[7,4]],'d');
        poly([[12,2],[13,6],[10,9],[5,11],[6,7],[9,3]],'s');
        poly([[12,2],[10,6],[6,10],[6,7],[9,3]],'m');
        line(11,3,4,12,'h');line(7,7,7,5,'h');line(8,7,10,7,'m');
        pixel(3,13,'s');pixel(2,14,'d');pixel(3,3,'g');pixel(3,2,'l');pixel(2,3,'l');
        pixel(12,12,'g');pixel(12,11,'l');pixel(11,12,'l');pixel(13,12,'l');pixel(12,13,'g');
    });
    icons.notices=sprite('拾取提醒铃',{d:'#826536',s:'#aa8645',m:'#cba358',h:'#ead088',l:'#f6e2a9'},({pixel,box,line,poly})=>{
        box(7,1,2,2,'s');pixel(7,1,'h');
        poly([[6,3],[10,3],[12,6],[12,10],[14,12],[14,13],[2,13],[2,12],[4,10],[4,6]],'d');
        poly([[6,4],[10,4],[11,6],[11,10],[13,12],[4,12],[5,10],[5,6]],'m');
        poly([[6,5],[8,4],[8,10],[6,11],[5,11],[6,9]],'h');
        line(6,5,7,4,'l');line(4,12,12,12,'h');line(10,6,10,10,'s');
        box(6,14,4,1,'d');box(7,13,2,1,'h');pixel(7,14,'m');
    });
    icons.loot=sprite('晶石光柱',{d:'#62537c',s:'#8a73a4',m:'#b39ec7',h:'#d8c9de',l:'#f0e5ef'},({pixel,box,line,poly})=>{
        box(7,1,1,6,'m');box(8,1,1,6,'h');pixel(7,7,'s');pixel(8,7,'m');
        poly([[7,8],[11,9],[12,12],[8,15],[4,13],[4,10]],'d');
        poly([[7,9],[10,10],[8,14],[5,12]],'m');
        poly([[7,9],[8,10],[5,12],[5,11]],'l');
        poly([[8,10],[10,10],[10,12],[8,14]],'s');
        line(8,10,8,13,'h');pixel(4,5,'h');pixel(11,3,'m');pixel(12,6,'l');
    });
    icons.appearance=sprite('染色画笔',{d:'#655441',s:'#937553',m:'#bd9e70',h:'#dec49b',i:'#9caaa8',w:'#d5dcce',c:'#5e9291',l:'#9ec5b1'},({pixel,box,line,poly})=>{
        poly([[12,1],[15,3],[10,9],[8,11],[5,8]],'d');
        poly([[12,2],[14,3],[9,9],[7,9]],'s');line(12,3,8,8,'m');pixel(12,2,'h');
        poly([[6,7],[10,10],[8,12],[4,9]],'i');line(6,7,9,10,'w');
        poly([[4,9],[8,12],[6,15],[2,14],[1,12]],'c');
        poly([[4,10],[6,12],[4,14],[2,13]],'l');pixel(2,14,'m');pixel(1,14,'s');
    });
    icons.search=sprite('搜索',{d:'#556570',m:'#99abb2',h:'#d3ddda'},({pixel,line,ellipse})=>{
        ellipse(6,6,4.3,4.3,'d');ellipse(6,6,3.3,3.3,'m');
        // Erase the lens interior after constructing the ring below.
        line(9,9,13,13,'d');line(10,9,14,13,'m');line(4,3,7,3,'h');pixel(3,4,'h');
    });
    // A transparent lens keeps this symbol light over both panel palettes.
    for(let y=4;y<=8;y++)for(let x=4;x<=8;x++)if((x-6)**2+(y-6)**2<=7){const row=[...icons.search.rows[y]];row[x]='.';icons.search.rows[y]=row.join('')}
    const arrowPalette={s:'#84908f',h:'#d7ded7'};
    icons.return=sprite('返回',arrowPalette,({pixel,box,line})=>{
        line(3,8,7,4,'s');line(3,8,7,12,'s');box(5,8,9,2,'s');
        line(3,7,7,3,'h');line(3,7,7,11,'h');box(5,7,9,1,'h');
    });
    icons.down=sprite('展开',arrowPalette,({line})=>{line(4,6,8,10,'s');line(8,10,12,6,'s');line(4,5,8,9,'h');line(8,9,12,5,'h')});
    icons.up=sprite('收起',arrowPalette,({line})=>{line(4,10,8,6,'s');line(8,6,12,10,'s');line(4,9,8,5,'h');line(8,5,12,9,'h')});
    window.ORIGINAL_ICONS=icons;
})();
