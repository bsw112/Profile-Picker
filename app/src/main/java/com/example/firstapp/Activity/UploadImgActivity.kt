package com.example.firstapp.Activity

import LoadingDialogFragment
import android.Manifest
import android.content.Intent
import android.content.Intent.*
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore.ACTION_IMAGE_CAPTURE
import android.provider.MediaStore.Images
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.android.volley.Response
import com.android.volley.toolbox.Volley
import com.example.firstapp.R
import com.example.firstapp.Adapter.UploadImgAdapter
import com.example.firstapp.VolleyMultipartRequest
import kotlinx.android.synthetic.main.activity_upload_img.*
import java.io.*

//업로드가 끝나면 액티비티도 끝낸다.
class UploadImgActivity : AppCompatActivity() {

    private val REQUEST_IMAGE_CAPTURE = 1
    private val REQUEST_PICK_FROM_ALBUM = 2
    private val REQUEST_PERMISSIONS = 3
    private val mPictureArray: ArrayList<MyBitmap> = ArrayList<MyBitmap>()
    private lateinit var mBitmapAdapter: ArrayAdapter<MyBitmap>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload_img)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onStart() {
        super.onStart()
        btn_post_addPicture.setOnClickListener {
            if (mPictureArray.size >= 5) {
                Toast.makeText(this, "사진은 5장까지만 등록할 수 있습니다.", Toast.LENGTH_LONG).show()
            } else
                dispatchGalleryIntent()
        }

        tv_post_save.setOnClickListener { uploadPostToServer() }

        mBitmapAdapter = UploadImgAdapter(
            this,
            R.layout.upload_img_item, mPictureArray
        )
        gv_picture.adapter = mBitmapAdapter
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != AppCompatActivity.RESULT_OK)
            return;

        //이미캡쳐 리퀘스트였고, 그 결과가 성공이면
        if (requestCode === REQUEST_IMAGE_CAPTURE) {
            //데이터에서 번들을 뽑아내고, 번들에서 비트맵을 뽑아내서 iv_profile1에 적용한다.
            val extras: Bundle? = data?.extras
            val bitmap = extras?.get("data") as Bitmap
            mPictureArray.add(MyBitmap("unnamed", bitmap))
            //  mPicture = getBitmapFromDataTest(data)
        } else if (requestCode == REQUEST_PICK_FROM_ALBUM) {
            for (myBitmap in getBitmapFromData(data))
                mPictureArray.add(myBitmap)
        }

        //픽쳐가 추가되었음을 알리고, 화면을 갱신하라고한다.
        mBitmapAdapter.notifyDataSetChanged()


    }

    private fun uploadPostToServer() {

        //Loading Dialog
        val loadingDialog = LoadingDialogFragment()

        val volleyMultipartRequest: VolleyMultipartRequest = object : VolleyMultipartRequest(
            Method.POST, getString(R.string.urlToServer) + "writePost/",
            Response.Listener {
                Toast.makeText(applicationContext, "게시물을 등록하였습니다.", Toast.LENGTH_LONG).show()
                loadingDialog.dismiss();
                finish();
            },
            Response.ErrorListener { error ->
                Toast.makeText(applicationContext, error.message, Toast.LENGTH_LONG).show()
                Log.e("GotError", "" + error.message)
            }) //여기까지가 object가 구현하는 VolleyMultipartRequest
        //여기서부터는 익명클래스 정의부분
        {
            override fun getByteData(): ArrayList<Pair<String, DataPart>> {
                //map이 중복허용안해서 arrayList로 바꿨다.
                val params = ArrayList<Pair<String, DataPart>>()
                //모든 사진을 바이트배열로 변환해서 바디에 쓴다.
                //key는 html form뷰의 name 항목. 즉, 파라미터가 되는듯
                for (picture in mPictureArray) {
                    params.add(
                        Pair("image", DataPart(picture.imgName, getFileDataFromDrawable(picture.bitmap), "image/webp")
                        )
                    )
                }

                return params
            }

            override fun getParams(): MutableMap<String, String> {
                //map은 중복허용 안함. 주의!
                val params: MutableMap<String, String> = HashMap()

                params.put("content", et_post_content.text.toString())
                params.put("title", et_post_title.text.toString())
                LoginActivity.mAccount?.email?.let {
                    params.put("email", it)
                }
                return params
            }


        }


        //adding the request to volley
        val requestQ = Volley.newRequestQueue(this)
        requestQ.add(volleyMultipartRequest)

        loadingDialog.show(supportFragmentManager, "다이어로그")

    }

    fun getFileDataFromDrawable(bitmap: Bitmap): ByteArray? {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.WEBP, 60, byteArrayOutputStream)
        return byteArrayOutputStream.toByteArray()
    }

    private fun dispatchTakePictureIntent() {
        /*
        카메라 기능이 있는 앱을 찾아서 실행시킨다.
        만약, 그런 앱이 없는데  startActivity()를 호출하면 앱이 정지된다.
        따라서 resolveActivity로 그 결과가 null인지 아닌지에 따라서 startActivity를 수행한다.
         */
        Intent(ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            //packageManager가 null이 아니면 수행하라.
            packageManager?.let {
                //resolveActivity 의 결과가 null이 아니면 수행하라.
                takePictureIntent.resolveActivity(it)?.also {
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        var isDenied: Boolean = false;

        //권한이 하나라도 거부되었나 체크
        for (result in grantResults) {
            if (result == PackageManager.PERMISSION_DENIED)
                isDenied = true
        }

        //권한이 거부되었을때
        if (isDenied) {

        } else
            pickPictureFromGallay()

    }

    private fun pickPictureFromGallay() {
        //이미 퍼미션을 받았으면 바로 그냥 갤러리로
        Intent(ACTION_GET_CONTENT).let {
//            it.type = Images.Media.CONTENT_TYPE
            it.type = "image/*"

            it.putExtra(EXTRA_ALLOW_MULTIPLE, true)
            startActivityForResult(createChooser(it, "Select Picture"), REQUEST_PICK_FROM_ALBUM)
        }
    }


    @RequiresApi(Build.VERSION_CODES.M)
    private fun dispatchGalleryIntent() {
        //외부 스토리지 쓰기, 읽기 퍼미션이 있는지 먼저 확인한다.
        if ((ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED) &&
            (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED)
        ) {
            //이미 퍼미션을 받았으면 바로 그냥 갤러리로
            pickPictureFromGallay()

        } else {
            //만약 교육용 UI를 표시해야한다면 (사용자가 퍼미션을 거부한적이 있으면)
            if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)
            ) {
                //교육용 UI보여주기
                Toast.makeText(applicationContext, "권한이 필요합니다", Toast.LENGTH_SHORT).show()
            }

            //퍼미션 요청
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ), REQUEST_PERMISSIONS
            )

        }

    }

    private fun getBitmapFromData2(data: Intent?): ArrayList<MyBitmap> {
        var test = data?.data;
        val clipData = data?.clipData ?: return ArrayList<MyBitmap>()
        var result = ArrayList<MyBitmap>()
        for (i in 0 until clipData.itemCount) {
            val photoUri: Uri = clipData.getItemAt(i).uri
            //데이터베이스 쿼리를 받기 위한 객체. 쿼리 결과에 대한 랜덤액세스를 제공한다.
            var cursor: Cursor? = null

            //Uri 스키마를 content:/// 에서 file:/// 로  변경한다.
            val proj = arrayOf(Images.Media.DATA)
            //contentResolver를 통해 contentProvider에 쿼리한다. photoUri 에 있는 Images.Media.DATA에 해당하는 데이터를 가져와라.
            cursor = photoUri?.let { contentResolver.query(it, proj, null, null, null) }

            //cursor은 Closeable 을 구현하기 때문에 use를 쓸수있다.
            //익셉션이 발생하건 말건 마지막에 close(리소스반환)한다. 자바의 try-with-resource와 유사하다.
            cursor?.use {
                //받아온 데이터가 있으면
                if (it.moveToFirst()) {
                    //쿼리결과에서 Images.Media.DATA를 가져올 수 있는 인덱스를 얻는다.
                    val columnIndex: Int = it?.getColumnIndexOrThrow(Images.Media.DATA) ?: -1

                    //파일을 얻는다.
                    val file = File(it?.getString(columnIndex))

                    //그냥 빈 옵션을 준비
                    val options = BitmapFactory.Options()
                    //파일을 디코딩해서 비트맵으로 만든다.
                    result.add(
                        MyBitmap(file.name, BitmapFactory.decodeFile(file.absolutePath, options))
                    )
                }
            }
        }
        return result
    }


    private fun getBitmapFromData(data: Intent?): ArrayList<MyBitmap> {

        //하나 이상을 선택할경우 clipData에 uri가  들어가고
        //하나만 선택할경우 data.data에 uri가 들어감.. 왜 그렇게 만들었는지 모르겠음.
        val result = ArrayList<MyBitmap>()
        val uriList = ArrayList<Uri>()

        val clipData = data?.clipData

        if (clipData == null)
            data?.data?.let { uriList.add(it) };
        else {
            if (clipData.itemCount > 5)
                Toast.makeText(this, "사진은 5장까지 추가가능합니다.", Toast.LENGTH_SHORT)

            for (i in 0 until clipData.itemCount.coerceAtMost(5)) {
                uriList.add(clipData.getItemAt(i).uri)
            }
        }

        var cnt = 0
        for (photoUri in uriList) {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, photoUri)
                ImageDecoder.decodeBitmap(source);
            } else {
                contentResolver.openInputStream(photoUri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
            }
            bitmap?.let {
                result.add(MyBitmap(System.currentTimeMillis().toString() + cnt++, it))
            }
        }
        return result
    }
}

data class MyBitmap(val imgName: String, val bitmap: Bitmap)
