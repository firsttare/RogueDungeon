
package com.example.roguelike

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.graphics.*
import android.media.ToneGenerator
import android.media.AudioManager
import android.view.*
import kotlin.math.abs
import kotlin.random.Random

class MainActivity:Activity(){
    override fun onCreate(b:Bundle?){super.onCreate(b);setContentView(GameView())}
    inner class GameView:View(this@MainActivity){
        private val rng=Random(System.currentTimeMillis())
        private val p=Paint(Paint.ANTI_ALIAS_FLAG)
        private val save=getSharedPreferences("save",Context.MODE_PRIVATE)
        private val tone=ToneGenerator(AudioManager.STREAM_MUSIC,70)
        private var mode=0 // 0 title, 1 game, 2 shop
        private var map=Array(25){CharArray(17){'#'}}
        private var px=8;private var py=12;private var sx=1;private var sy=1
        private var hp=30;private var maxHp=30;private var hunger=100;private var level=1
        private var exp=0;private var gold=0;private var floor=1;private var score=0
        private var sword=4;private var shield=0;private var arrows=8;private var food=3;private var potions=2
        private var identified=false;private var gameOver=false;private var message=""
        private var hasRing=false;private var hasStaff=false;private var swordPlus=0;private var shieldPlus=0
        private var shopX=1;private var shopY=1
        private val traps=mutableSetOf<Pair<Int,Int>>()
        private val items=mutableListOf<Item>()
        private val enemies=mutableListOf<Enemy>()
        data class Enemy(var x:Int,var y:Int,var hp:Int,var type:Int,var sleep:Int=0)
        data class Item(var x:Int,var y:Int,var type:Int,var known:Boolean=false)

        init{p.typeface=Typeface.create("sans",0);isFocusable=true}

        override fun onDraw(c:Canvas){
            c.drawColor(Color.rgb(7,7,7))
            if(mode==0){drawTitle(c);return}
            if(mode==2){drawShop(c);return}
            val cell=minOf(width/17f,height*.67f/25f);val left=(width-17*cell)/2;val top=38f
            p.textAlign=Paint.Align.CENTER
            for(y in 0..24)for(x in 0..16){
                p.color=if(map[y][x]=='#')Color.rgb(35,35,40) else Color.rgb(73,68,58)
                c.drawRect(left+x*cell,top+y*cell,left+(x+1)*cell-1,top+(y+1)*cell-1,p)
            }
            p.textSize=cell*.62f;p.color=Color.YELLOW;c.drawText(">",left+sx*cell+cell/2,top+sy*cell+cell*.72f,p)
            p.color=Color.MAGENTA;if(shopX>=0)c.drawText("$",left+shopX*cell+cell/2,top+shopY*cell+cell*.72f,p)
            for(t in traps){p.color=Color.DKGRAY;c.drawText("^",left+t.first*cell+cell/2,top+t.second*cell+cell*.72f,p)}
            for(i in items){p.color=when(i.type){0->Color.WHITE;1->Color.CYAN;2->Color.GREEN;3->Color.YELLOW;4->Color.LTGRAY;else->Color.RED};c.drawText(if(i.known||identified) itemName(i.type) else "?",left+i.x*cell+cell/2,top+i.y*cell+cell*.72f,p)}
            for(e in enemies){p.color=when(e.type){0->Color.RED;1->Color.rgb(220,120,40);2->Color.BLUE;else->Color.GREEN};c.drawText(enemyName(e.type),left+e.x*cell+cell/2,top+e.y*cell+cell*.72f,p)}
            p.color=Color.CYAN;c.drawText("@",left+px*cell+cell/2,top+py*cell+cell*.72f,p)
            p.textSize=15f;p.color=Color.WHITE;c.drawText("HP $hp/$maxHp  満腹 $hunger  Lv$level  経験 $exp  金$gold",width/2f,25f,p)
            p.textSize=13f;c.drawText("剣 ${sword+swordPlus} 盾 $shieldPlus  矢$arrows  食料$food  薬$potions",width/2f,top+25*cell+17,p)
            p.color=Color.LTGRAY;c.drawText(message,width/2f,top+25*cell+36,p)
            button(c,width*.16f,height*.82f,"←");button(c,width*.50f,height*.77f,"↑");button(c,width*.84f,height*.82f,"→");button(c,width*.50f,height*.90f,"↓")
            button(c,width*.16f,height*.93f,"食");button(c,width*.37f,height*.93f,"薬");button(c,width*.63f,height*.93f,"杖");button(c,width*.84f,height*.93f,"弓")
            if(gameOver){p.color=Color.argb(225,0,0,0);c.drawRect(0f,0f,width.toFloat(),height.toFloat(),p);p.color=Color.WHITE;p.textSize=34f;c.drawText("力尽きた",width/2f,height*.42f,p);p.textSize=17f;c.drawText("タップでタイトルへ",width/2f,height*.50f,p)}
        }
        private fun drawTitle(c:Canvas){
            p.textAlign=Paint.Align.CENTER;p.color=Color.WHITE;p.textSize=38f;c.drawText("迷宮の旅人",width/2f,height*.28f,p)
            p.textSize=18f;p.color=Color.LTGRAY;c.drawText("ターン制ローグライク",width/2f,height*.34f,p)
            p.textSize=20f;p.color=Color.YELLOW;c.drawText("画面をタップして開始",width/2f,height*.52f,p)
            p.textSize=14f;p.color=Color.GRAY;c.drawText("ランダム迷宮 / 装備 / 店 / 魔法 / 罠 / セーブ",width/2f,height*.60f,p)
            val best=save.getInt("best",0);c.drawText("最高スコア $best",width/2f,height*.66f,p)
        }
        private fun drawShop(c:Canvas){
            c.drawColor(Color.rgb(18,12,8));p.textAlign=Paint.Align.CENTER;p.color=Color.YELLOW;p.textSize=32f;c.drawText("商店",width/2f,100f,p)
            p.color=Color.WHITE;p.textSize=19f;c.drawText("所持金: $gold G",width/2f,145f,p)
            val lines=listOf("1: 回復薬 10G","2: 食料 8G","3: 矢10本 12G","4: 剣強化 40G","5: 盾強化 40G","画面タップで戻る")
            lines.forEachIndexed{i,s->c.drawText(s,width/2f,210f+i*55,p)}
        }
        private fun button(c:Canvas,x:Float,y:Float,s:String){p.color=Color.rgb(45,45,50);c.drawCircle(x,y,32f,p);p.color=Color.WHITE;p.textSize=22f;c.drawText(s,x,y+8,p)}
        override fun onTouchEvent(e:MotionEvent):Boolean{
            if(e.action!=MotionEvent.ACTION_UP)return true
            if(mode==0){newGame();return true}
            if(gameOver){mode=0;invalidate();return true}
            if(mode==2){mode=1;invalidate();return true}
            val x=e.x;val y=e.y;val w=width.toFloat();val h=height.toFloat()
            when{
                x<w*.30&&y>h*.76->move(-1,0)
                x>w*.70&&y>h*.76->move(1,0)
                x in w*.35..w*.65&&y>h*.72&&y<h*.86->move(0,-1)
                x in w*.35..w*.65&&y>h*.86->move(0,1)
                x<w*.28&&y>h*.89->eat()
                x in w*.28..w*.49&&y>h*.89->drink()
                x in w*.49..w*.74&&y>h*.89->cast()
                x>w*.74&&y>h*.89->shoot()
            };return true
        }
        private fun newGame(){mode=1;floor=1;level=1;exp=0;gold=0;hp=30;maxHp=30;hunger=100;sword=4;shieldPlus=0;swordPlus=0;arrows=8;food=3;potions=2;gameOver=false;newFloor();message="第1階。階段を探そう";tone.startTone(ToneGenerator.TONE_PROP_BEEP,100)}
        private fun newFloor(){
            map=Array(25){CharArray(17){'#'}};repeat(13){val w=rng.nextInt(3,8);val h=rng.nextInt(3,7);val a=rng.nextInt(1,17-w);val b=rng.nextInt(1,25-h);for(j in b until b+h)for(i in a until a+w)map[j][i]='.'}
            var x=8;var y=12;repeat(120){map[y][x]='.';when(rng.nextInt(4)){0->x=(x+1).coerceAtMost(15);1->x=(x-1).coerceAtLeast(1);2->y=(y+1).coerceAtMost(23);else->y=(y-1).coerceAtLeast(1)}}
            val f=mutableListOf<Pair<Int,Int>>();for(j in 1..23)for(i in 1..15)if(map[j][i]=='.')f.add(i to j)
            val s=f.random(rng);px=s.first;py=s.second;val far=f.maxBy{abs(it.first-px)+abs(it.second-py)};sx=far.first;sy=far.second
            shopX=-1;items.clear();traps.clear();enemies.clear()
            repeat(5+floor){val q=f.filter{abs(it.first-px)+abs(it.second-py)>5}.randomOrNull();if(q!=null)enemies.add(Enemy(q.first,q.second,5+floor,rng.nextInt(4)))}
            repeat(5){val q=f.random();if(q!=(px to py)&&q!=(sx to sy))items.add(Item(q.first,q.second,rng.nextInt(5),false))}
            repeat(4){val q=f.random();traps.add(q)}
            if(floor%3==0){val q=f.random();shopX=q.first;shopY=q.second}
            invalidate()
        }
        private fun enemyName(t:Int)=when(t){0->"鬼";1->"蛇";2->"魔";else->"虫"}
        private fun itemName(t:Int)=when(t){0->"剣";1->"盾";2->"薬";3->"杖";4->"食";else->"？"}
        private fun move(dx:Int,dy:Int){
            val nx=px+dx;val ny=py+dy;if(nx !in 1..15||ny !in 1..23||map[ny][nx]=='#'){message="壁だ";invalidate();return}
            val e=enemies.firstOrNull{it.x==nx&&it.y==ny};if(e!=null){attack(e);turn();return}
            px=nx;py=ny;hunger--;if(hunger<=0){hp--;message="空腹でHP減少"};checkTile()
            if(px==sx&&py==sy){floor++;level++;maxHp+=3;hp=maxHp;score+=100;newFloor();message="階段を降りた！ 第$floor階"} else turn()
        }
        private fun attack(e:Enemy){val dmg=sword+swordPlus;rangedSound();e.hp-=dmg;message="攻撃！ $dmg ダメージ";if(e.hp<=0){enemies.remove(e);gold+=rng.nextInt(3,10);exp+=2;score+=10;if(exp>=level*5){exp=0;level++;maxHp+=3;hp=maxHp;message="レベルアップ！"}}}
        private fun turn(){for(e in enemies.toList()){if(e.sleep>0){e.sleep--;continue};if(abs(e.x-px)+abs(e.y-py)==1){hp-=maxOf(1,1+floor/4-shieldPlus);message="敵の攻撃！"}else if(abs(e.x-px)+abs(e.y-py)<8){val dx=if(px>e.x)1 else if(px<e.x)-1 else 0;val dy=if(py>e.y)1 else if(py<e.y)-1 else 0;val nx=e.x+dx;val ny=e.y+dy;if(nx in 1..15&&ny in 1..23&&map[ny][nx]=='.'&&enemies.none{it!==e&&it.x==nx&&it.y==ny}&&!(nx==px&&ny==py)){e.x=nx;e.y=ny}}};if(hp<=0){hp=0;gameOver=true;save.edit().putInt("best",maxOf(score,save.getInt("best",0))).apply()} ;invalidate()}
        private fun checkTile(){if(traps.remove(px to py)){hp-=rng.nextInt(2,6);message="罠を踏んだ！"};val it=items.firstOrNull{it.x==px&&it.y==py};if(it!=null){items.remove(it);when(it.type){0->swordPlus++;1->shieldPlus++;2->potions++;3->hasStaff=true;4->food++;};it.known=true;message="アイテムを拾った: ${itemName(it.type)}"};if(px==shopX&&py==shopY)mode=2}
        private fun eat(){if(food>0){food--;hunger=(hunger+35).coerceAtMost(100);message="食料を食べた";turn()}else message="食料がない";invalidate()}
        private fun drink(){if(potions>0&&hp<maxHp){potions--;hp=(hp+12).coerceAtMost(maxHp);message="薬で回復";turn()}else message="薬が使えない";invalidate()}
        private fun cast(){if(!hasStaff){message="杖を持っていない";invalidate();return};val target=enemies.minByOrNull{abs(it.x-px)+abs(it.y-py)};if(target!=null&&abs(target.x-px)+abs(target.y-py)<=5){target.hp-=10;message="魔法！ 10ダメージ";if(target.hp<=0)enemies.remove(target);turn()}else{message="魔法の射程に敵がいない";invalidate()}}
        private fun shoot(){if(arrows<=0){message="矢がない";invalidate();return};val target=enemies.firstOrNull{(it.x==px&&abs(it.y-py)<=4)||(it.y==py&&abs(it.x-px)<=4)};if(target!=null){arrows--;target.hp-=6;message="矢を放った！";if(target.hp<=0){enemies.remove(target);gold+=3}}else message="一直線上に敵がいない";turn()}
        private fun rangedSound(){tone.startTone(ToneGenerator.TONE_PROP_ACK,70)}
    }
}
