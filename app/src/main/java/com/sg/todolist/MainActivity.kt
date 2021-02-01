package com.sg.todolist

import android.app.Activity
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.activity.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.sg.todolist.databinding.ActivityMainBinding
import com.sg.todolist.databinding.ItemTodoBinding

class MainActivity : AppCompatActivity() {
    val RC_SIGN_IN = 1000;

    private lateinit var binding: ActivityMainBinding//activity_main.xml을 의미한다

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        //로그인 안 됨
        FirebaseAuth.getInstance().currentUser?.let {
            login()
        }

        //data.add(Todo("숙제"))
        //data.add(Todo("장보기", true))

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = TodoAdapter(
                emptyList(),
                onClickDeleteIcon = {
                    //deleteTodo(it)
                    viewModel.deleteTodo(it)
                },
                onClickItem = {
                    //toggleTodo(it)
                    viewModel.toggleTodo(it)
                }
            )
        }

        binding.addButton.setOnClickListener {
            val todo = Todo(binding.editText.text.toString())
            //addTodo(todo)
            viewModel.addTodo(todo)
            binding.editText.setText("")
        }

        //관찰 -> UI 업데이트
        viewModel.todoLiveData.observe(this, Observer {
            //여기서 데이터를 관찰하고 있다가 데이터가 변경되면 다음 함수를 실행한다
            (binding.recyclerView.adapter as TodoAdapter).setData(it)
        })


    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {

            if (resultCode == Activity.RESULT_OK) {
                // Successfully signed in
                viewModel.fetchData()

            } else {
                // Sign in failed.
                finish()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        return when (item.itemId) {
            R.id.action_logout -> {
                logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun login() {
        val providers = arrayListOf(AuthUI.IdpConfig.EmailBuilder().build())

        startActivityForResult(
            AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAvailableProviders(providers)
                .build(),
            RC_SIGN_IN)
    }

    fun logout() {
        AuthUI.getInstance()
            .signOut(this)
            .addOnCompleteListener {
                login()
            }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.main,menu)
        return true
    }

    //    private fun addTodo() {
//        val todo = Todo(binding.editText.text.toString())
//        data.add(todo)//여기서 끝내면 화면이 변하지 않는다. adapter에 알려줘야 한다.
//        binding.recyclerView.adapter?.notifyDataSetChanged()//adapter에 알려주기
//    }
//
//    private fun deleteTodo(todo: Todo) {
//        data.remove(todo)
//        binding.recyclerView.adapter?.notifyDataSetChanged()//adapter에 알려주기
//    }
//
//    private fun toggleTodo(todo: Todo) {
//        todo.isDone = !todo.isDone
//        binding.recyclerView.adapter?.notifyDataSetChanged()//adapter에 알려주기
//    }
}

data class Todo(
    val text: String,
    var isDone: Boolean = false
)

class TodoAdapter(
    private var dataSet: List<DocumentSnapshot>,
    val onClickDeleteIcon: (todo: DocumentSnapshot) -> Unit,
    val onClickItem: (todo: DocumentSnapshot) -> Unit
) :
    RecyclerView.Adapter<TodoAdapter.TodoViewHolder>() {

    class TodoViewHolder(val binding: ItemTodoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): TodoViewHolder {
        //item 하나에 들어갈 view를 설정한다
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.item_todo, viewGroup, false)

        return TodoViewHolder(ItemTodoBinding.bind(view))
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: TodoViewHolder, position: Int) {
        val todo = dataSet[position]
        viewHolder.binding.todoText.text = todo.getString("text") ?: ""

        if (todo.getBoolean("isDone") ?: false) {
            //할 일 완료
            viewHolder.binding.todoText.apply {
                paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                setTypeface(null, Typeface.ITALIC)
            }
        } else {
            viewHolder.binding.todoText.apply {
                paintFlags = 0
                setTypeface(null, Typeface.NORMAL)
            }
        }

        viewHolder.binding.deleteImageView.setOnClickListener {
            onClickDeleteIcon.invoke(todo)
        }

        viewHolder.binding.root.setOnClickListener {
            onClickItem.invoke(todo)
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = dataSet.size

    fun setData(newData: List<DocumentSnapshot>) {
        //이 함수를 호출할 때 마다 UI를 갱신하도록 한다
        dataSet = newData
        notifyDataSetChanged()
    }
}

class MainViewModel: ViewModel() {
    val db = Firebase.firestore

    val todoLiveData = MutableLiveData<List<DocumentSnapshot>>()//Mutable을 안 붙이면 data변경이 불가능 하므로 반드시 붙여야 한다

    //delete를 하려면 그 대상의 id가 필요한데 이는 위의 for문의 document에 있다.
    // 따라서 data의 generic을 QueryDocumentSnapShot으로 변경하자
    //private val data = arrayListOf<Todo>()
    //private val data = arrayListOf<QueryDocumentSnapshot>()

    init {
        fetchData()
    }

    fun fetchData() {
        //현재 로그인 된 유저의 UID 가져오기
        val user = FirebaseAuth.getInstance().currentUser
        if(user != null) {
            db.collection(user.uid)
//                .get()
//                .addOnSuccessListener {result ->
//                    data.clear()
//                    for (document in result) {
//                        val text : String? = document.data["text"] as String ?: null
//                        val isDone : Boolean? = document.data["isDone"] as Boolean ?: null
//                        if(text != null && isDone != null) {
//                            data.add(Todo(text, isDone))
//                        }
//                    }
//                    todoLiveData.value = data
//                }
            //위의 코드는 매번 get을 해야한다. 실시간으로 데이터가 변화할 때 마다 데이터를 업데이트 하는 listener를 추가한다
                    //이러면 firebase에서 데이터를 생성하면 app에 자동으로 할일이 등록된다
                .addSnapshotListener { value, error ->
                    if (error != null) {
                        return@addSnapshotListener
                    }

                    if (value != null) {
                        todoLiveData.value = value.documents
                    }
/*
                    data.clear()
                    for (document in value!!) {
                        val text : String? = document.getString("text") ?: null
                        val isDone : Boolean? = document.getBoolean("isDone") ?: null
                        if(text != null && isDone != null) {
                            data.add(document)
                        }
                    }
                    todoLiveData.value = data
 */
                }
        }
    }

    fun addTodo(todo: Todo) {
        FirebaseAuth.getInstance().currentUser?.let { user ->
            // user가 null이 아닐 경우 실행(let) 된다
            db.collection(user.uid).add(todo)
        }
        //data.add(todo)
        //todoLiveData.value = data//observer가 관찰하다가 여기서 데이터가 변경됨을 감지한다
        //SnapshotListener를 등록하면 위의 코드들은 더이상 필요가 없다
    }

   fun deleteTodo(todo: DocumentSnapshot) {
//        data.remove(todo)
//        todoLiveData.value = data
       FirebaseAuth.getInstance().currentUser?.let { user ->
           // user가 null이 아닐 경우 실행(let) 된다
           db.collection(user.uid).document(todo.id).delete()
       }
    }

    fun toggleTodo(todo: DocumentSnapshot) {
        //update
        FirebaseAuth.getInstance().currentUser?.let { user ->
            // user가 null이 아닐 경우 실행(let) 된다
            val isDone = todo.getBoolean("isDone") ?: false
            db.collection(user.uid).document(todo.id).update("isDone",!isDone)
        }
        //todo.isDone = !todo.isDone
//        todoLiveData.value = data
    }
}

