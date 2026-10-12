const sharp=require('sharp');
const fs=require('node:fs/promises');
const path=require('node:path');
const crypto=require('node:crypto');
const assert=require('node:assert/strict');
const root=process.argv[2];
const hash=b=>crypto.createHash('sha256').update(b).digest('hex');
const raw=async b=>sharp(b).ensureAlpha().raw().toBuffer({resolveWithObject:true});
function samePixel(a,ai,b,bi){assert.equal(a[ai+3],b[bi+3]);if(a[ai+3]){for(let n=0;n<3;n++)assert.equal(a[ai+n],b[bi+n]);}}
(async()=>{
 const m=JSON.parse(await fs.readFile(path.join(root,'manifest.json'),'utf8'));
 assert.equal(m.themes.length,12);assert.deepEqual(m.shapeOrder,['I','O','T','S','Z','J','L']);
 assert.deepEqual(m.atlasSize,[924,528]);assert.equal(m.tileSize,128);assert.equal(m.pitch,132);assert.equal(m.padding,2);
 for(const f of m.files){const b=await fs.readFile(path.join(root,f.path));assert.equal(hash(b),f.sha256,f.path);assert.equal(b.length,f.bytes);}
 let sprites=0,cells=0,fragments=0,minCoverage=1;
 const diagnostic=[];const tile=128,pitch=132,pad=2;
 for(let ti=0;ti<m.themes.length;ti++){
  const t=m.themes[ti],atlas=await raw(path.join(root,t.atlas));assert.equal(atlas.info.width,924);assert.equal(atlas.info.height,528);assert.equal(t.figures.length,7);
  for(let si=0;si<7;si++){
   const f=t.figures[si];assert.equal(f.shape,m.shapeOrder[si]);assert.equal(f.cells.length,4);
   const sprite=await raw(path.join(root,f.sprite));assert.equal(sprite.info.width,f.grid[0]*tile);assert.equal(sprite.info.height,f.grid[1]*tile);
   const occupied=new Set(f.cells.map(c=>c.spritePosition.join(',')));
   for(let gy=0;gy<f.grid[1];gy++)for(let gx=0;gx<f.grid[0];gx++)if(!occupied.has(`${gx*tile},${gy*tile}`))for(let y=0;y<tile;y++)for(let x=0;x<tile;x++)assert.equal(sprite.data[((gy*tile+y)*sprite.info.width+gx*tile+x)*4+3],0);
   for(let ci=0;ci<4;ci++){
    const c=f.cells[ci];assert.equal(c.canonicalIndex,ci);assert.deepEqual(c.canonicalPosition,m.canonicalCells[f.shape][ci]);assert.deepEqual(c.atlasRect,[si*pitch+pad,ci*pitch+pad,tile,tile]);
    let opaque=0;const [ax,ay]=c.atlasRect,[sx,sy]=c.spritePosition;
    for(let y=0;y<tile;y++)for(let x=0;x<tile;x++){
     const ai=((ay+y)*924+ax+x)*4,bi=((sy+y)*sprite.info.width+sx+x)*4;samePixel(atlas.data,ai,sprite.data,bi);if(atlas.data[ai+3]>240)opaque++;
    }
    const coverage=opaque/(tile*tile);minCoverage=Math.min(minCoverage,coverage);assert(coverage>.85,`${t.id}/${f.shape}/${ci}: insufficient solid coverage ${coverage}`);
    for(let y=0;y<pitch;y++)for(let x=0;x<pitch;x++)if(x<pad||x>=pad+tile||y<pad||y>=pad+tile){
     const ai=((ci*pitch+y)*924+si*pitch+x)*4;const ex=ax+Math.max(0,Math.min(tile-1,x-pad)),ey=ay+Math.max(0,Math.min(tile-1,y-pad));samePixel(atlas.data,ai,atlas.data,(ey*924+ex)*4);
    }
    const cell=await sharp(path.join(root,t.atlas)).extract({left:ax,top:ay,width:tile,height:tile}).png().toBuffer();
    const parts=[];
    for(let qy=0;qy<2;qy++)for(let qx=0;qx<2;qx++){parts.push({input:await sharp(cell).extract({left:qx*64,top:qy*64,width:64,height:64}).png().toBuffer(),left:qx*64,top:qy*64});fragments++;}
    // Reassemble exact decoded samples, without compositing's alpha round-trip rounding.
    const rebuilt=Buffer.alloc(128*128*4);
    for(const part of parts){const piece=await raw(part.input);for(let y=0;y<64;y++)piece.data.copy(rebuilt,((part.top+y)*128+part.left)*4,y*64*4,(y+1)*64*4);}
    const original=await raw(cell);for(let p=0;p<original.data.length;p+=4)samePixel(rebuilt,p,original.data,p);
    if(si===4&&ci===2){
     const col=ti%3,row=Math.floor(ti/3),bx=col*360,by=row*200;
     diagnostic.push({input:cell,left:bx+12,top:by+45});
     for(let q=0;q<4;q++)diagnostic.push({input:parts[q].input,left:bx+181+(q%2)*74,top:by+45+Math.floor(q/2)*74});
    }
    cells++;
   }
   sprites++;
  }
 }
 const svg=`<svg width="1080" height="810"><rect width="100%" height="100%" fill="#24282c"/>${m.themes.map((t,i)=>`<text x="${i%3*360+12}" y="${Math.floor(i/3)*200+27}" fill="white" font-family="Arial" font-size="20">${t.label}</text>`).join('')}</svg>`;
 const diag=await sharp(Buffer.from(svg)).composite(diagnostic).png().toBuffer();await fs.writeFile(path.join(root,'previews/explosion-fragments.png'),diag);
 const report={result:'passed',atlasCount:12,figureCount:sprites,canonicalCellCount:cells,quadrantFragmentsChecked:fragments,checks:['asset SHA256/size inventory','atlas dimensions and RGBA decoding','7 shapes x 4 canonical cells for every theme','exact visible RGBA equality of atlas cores and sprite cells','empty shape positions fully transparent','extruded edge padding equals corresponding edge pixels','four quadrant crops reconstruct each original textured cell exactly'],minimumOpaqueCellCoverage:minCoverage,excluded:'No Android build, emulator test, runtime integration or automated aesthetic/edge-continuity claim.'};
 await fs.writeFile(path.join(root,'VALIDATION.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report,null,2));
})().catch(e=>{console.error(e);process.exitCode=1;});
