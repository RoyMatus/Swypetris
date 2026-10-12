// Mechanical sprite slicing, normalization and Android resource packaging.
// Artistic/background edits are supplied by the built-in image generation tool.
const sharp = require('sharp');
const fs = require('node:fs/promises');
const path = require('node:path');
const crypto = require('node:crypto');
const root = process.argv[2];
const input = process.argv[3];
if (!root || !input) throw Error('Usage: node package-textures.cjs <package-dir> <input-image-dir>');
const sha = b => crypto.createHash('sha256').update(b).digest('hex');
const shapes = ['I','O','T','S','Z','J','L'];
const coords = [ [[0,1],[1,1],[2,1],[3,1]], [[0,0],[1,0],[0,1],[1,1]], [[1,0],[0,1],[1,1],[2,1]], [[1,0],[2,0],[0,1],[1,1]], [[0,0],[1,0],[1,1],[2,1]], [[0,0],[0,1],[1,1],[2,1]], [[2,0],[0,1],[1,1],[2,1]] ];
const names=['classic','monokai','gruvbox','vscode','dracula','nord','solarized_light','solarized_dark','github_light','tokyo_night','catppuccin','synthwave_84'];
const labels=['Classic','Monokai','Gruvbox Dark','VS Code Dark+','Dracula','Nord','Solarized Light','Solarized Dark','GitHub Light','Tokyo Night','Catppuccin Mocha',"SynthWave '84"];
const cfg = [
 {ref:'exec-71d177f2-811e-499e-9dc6-5e4606c2e2c4.png',cut:'exec-d1035d4a-dc32-4839-ad70-7dde53ff6a19.png',xs:[80,357,540,775,1000,1220,1430,1650],ys:[75,267,465,673,879]},
 {ref:'exec-2a4fd84d-2e5f-4a77-91b8-a41187f2c885.png',cut:'exec-c24c481d-d470-40db-9965-c4dc6a985a71.png',xs:[30,340,540,810,1065,1320,1590,1840],ys:[60,230,409,584,761]},
 {ref:'exec-65da5949-2267-40d3-ad89-1cc9dd214e75.png',cut:'exec-95e66a42-6932-43b7-89fc-9656378e708a.png',xs:[90,390,580,824,1058,1290,1525,1760],ys:[75,248,434,618,807]}
];
const TILE=128, PAD=2, PITCH=TILE+PAD*2;
const manifest={schemaVersion:1,package:'Swypetris connected figure materials 269',baselineCommit:'ada8d174a5cdde32817379309ed2822d76b5813b',artwork:'Latest three figure catalogues selected by the owner; background/text removal by built-in imagegen. No palette recoloring.',tileSize:TILE,padding:PAD,pitch:PITCH,atlasSize:[7*PITCH,4*PITCH],alpha:'straight RGBA; keep transparency',shapeOrder:shapes,canonicalCells:Object.fromEntries(shapes.map((s,i)=>[s,coords[i]])),iPreviewOrigin:[0,1],oEffectiveQuarterTurns:0,themes:[],sources:[],files:[]};
const clear=(w,h)=>sharp({create:{width:w,height:h,channels:4,background:{r:0,g:0,b:0,alpha:0}}});
async function save(rel,buf){await fs.mkdir(path.dirname(path.join(root,rel)),{recursive:true});await fs.writeFile(path.join(root,rel),buf);manifest.files.push({path:rel,bytes:buf.length,sha256:sha(buf)});}
function bounds(data,info,x0,y0,x1,y1){
 const xc=[],yc=[];
 for(let x=x0;x<x1;x++){let n=0;for(let y=y0;y<y1;y++)if(data[(y*info.width+x)*4+3]>240)n++;if(n>12)xc.push(x);}
 for(let y=y0;y<y1;y++){let n=0;for(let x=x0;x<x1;x++)if(data[(y*info.width+x)*4+3]>240)n++;if(n>12)yc.push(y);}
 if(!xc.length||!yc.length)throw Error('Missing figure');
 return [xc[0],yc[0],xc.at(-1)+1,yc.at(-1)+1];
}
async function extrude(png){
 const {data,info}=await sharp(png).ensureAlpha().raw().toBuffer({resolveWithObject:true});
 const out=Buffer.alloc(PITCH*PITCH*4);
 for(let y=0;y<PITCH;y++)for(let x=0;x<PITCH;x++){
  const sx=Math.min(TILE-1,Math.max(0,x-PAD)),sy=Math.min(TILE-1,Math.max(0,y-PAD));
  data.copy(out,(y*PITCH+x)*4,(sy*info.width+sx)*4,(sy*info.width+sx)*4+4);
 }
 return sharp(out,{raw:{width:PITCH,height:PITCH,channels:4}}).png().toBuffer();
}
(async()=>{
 for(let g=0;g<3;g++){
  const {ref,cut,xs,ys}=cfg[g];
  const sourceFile=async(kind,f)=>{const packaged=path.join(input,kind,`sheet-${g+1}.png`);try{await fs.access(packaged);return packaged;}catch{return path.join(input,f);}};
  for(const [kind,f] of [['references',ref],['sources',cut]]){
   const b=await fs.readFile(await sourceFile(kind,f));const rel=`${kind}/sheet-${g+1}.png`;await save(rel,b);manifest.sources.push({role:kind,file:rel,sha256:sha(b),...await sharp(b).metadata().then(m=>({width:m.width,height:m.height,hasAlpha:m.hasAlpha}))});
  }
  const image=await fs.readFile(await sourceFile('sources',cut));
  const {data,info}=await sharp(image).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  const preview=[];
  for(let row=0;row<4;row++){
   const theme=names[g*4+row];const tiles=[];const themeMeta={id:theme,label:labels[g*4+row],resource:`material_connected_${theme}`,atlas:`android/app/src/main/res/drawable-nodpi/material_connected_${theme}.webp`,figures:[]};
   for(let col=0;col<7;col++){
    const b=bounds(data,info,xs[col],ys[row],xs[col+1],ys[row+1]);
    const cells=coords[col];const minY=Math.min(...cells.map(c=>c[1]));const gw=Math.max(...cells.map(c=>c[0]))+1,gh=Math.max(...cells.map(c=>c[1]))+1-minY;
    const figureMeta={shape:shapes[col],sprite:`sprites/${theme}/tetromino_${theme}_${shapes[col].toLowerCase()}.png`,sourceSheet:`sources/sheet-${g+1}.png`,sourceBounds:b,grid:[gw,gh],cells:[]};
    const composites=[];
    for(let k=0;k<4;k++){
     const [cx,engineY]=cells[k];const cy=engineY-minY;
     const left=Math.round(b[0]+cx*(b[2]-b[0])/gw),right=Math.round(b[0]+(cx+1)*(b[2]-b[0])/gw);
     const top=Math.round(b[1]+cy*(b[3]-b[1])/gh),bottom=Math.round(b[1]+(cy+1)*(b[3]-b[1])/gh);
     const tile=await sharp(image).extract({left,top,width:right-left,height:bottom-top}).resize(TILE,TILE,{fit:'fill',kernel:'lanczos3'}).png().toBuffer();
     composites.push({input:tile,left:cx*TILE,top:cy*TILE});
     tiles.push({input:await extrude(tile),left:col*PITCH,top:k*PITCH});
     figureMeta.cells.push({canonicalIndex:k,canonicalPosition:[cx,engineY],spritePosition:[cx*TILE,cy*TILE],sourceCrop:[left,top,right-left,bottom-top],atlasRect:[col*PITCH+PAD,k*PITCH+PAD,TILE,TILE]});
    }
    const sprite=await clear(gw*TILE,gh*TILE).composite(composites).png().toBuffer();await save(figureMeta.sprite,sprite);themeMeta.figures.push(figureMeta);
    const cell=44;const disp=await sharp(sprite).resize(gw*cell,gh*cell).png().toBuffer();preview.push({input:disp,left:220+col*198+Math.round((184-gw*cell)/2),top:row*170+50+Math.round((90-gh*cell)/2)});
   }
   await save(themeMeta.atlas,await clear(7*PITCH,4*PITCH).composite(tiles).webp({lossless:true,effort:6}).toBuffer());
   manifest.themes.push(themeMeta);
  }
  const svg=`<svg width="1640" height="690"><rect width="100%" height="100%" fill="#22272b"/>${Array.from({length:4},(_,r)=>`<text x="16" y="${r*170+93}" fill="#fff" font-family="Arial" font-size="20">${labels[g*4+r].replaceAll('&','&amp;')}</text>`).join('')}${shapes.map((s,c)=>`<text x="${300+c*198}" y="24" fill="#fff" font-family="Arial" font-size="18">${s}</text>`).join('')}</svg>`;
  await save(`previews/runtime-figures-${g+1}.png`,await sharp(Buffer.from(svg)).composite(preview).png().toBuffer());
 }
 await fs.writeFile(path.join(root,'manifest.json'),JSON.stringify(manifest,null,2)+'\n');
 console.log(JSON.stringify({themes:manifest.themes.length,figures:manifest.themes.reduce((n,t)=>n+t.figures.length,0),tiles:manifest.themes.length*28,tileSize:TILE,atlasSize:manifest.atlasSize,files:manifest.files.length,output:root},null,2));
})().catch(e=>{console.error(e);process.exitCode=1;});
